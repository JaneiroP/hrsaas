package com.ohmdevs.hrsaas.time.application;

import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import com.ohmdevs.hrsaas.time.domain.LeaveBalance;
import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveBalanceRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveRequestRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Solicitudes de ausencia y sus saldos. El saldo ({@link LeaveBalance}) solo
 * se toca aquí — nunca directo desde un repositorio de otra capa — porque
 * {@code accruedDays}/{@code usedDays}/{@code availableDays} tienen que
 * moverse juntos o el saldo queda mintiendo.
 */
@Service
public class LeaveManagementService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveManagementService(LeaveTypeRepository leaveTypeRepository,
                                   LeaveBalanceRepository leaveBalanceRepository,
                                   LeaveRequestRepository leaveRequestRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional
    public LeaveType createLeaveType(String code, String name, boolean paid, boolean requiresApproval,
                                      BigDecimal accrualDaysPerYear, Integer maxCarryoverDays) {
        TenantContext.require();
        leaveTypeRepository.findByCode(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe un tipo de licencia '" + code + "' en este tenant");
        });
        LeaveType leaveType = new LeaveType();
        leaveType.setCode(code);
        leaveType.setName(name);
        leaveType.setPaid(paid);
        leaveType.setRequiresApproval(requiresApproval);
        leaveType.setAccrualDaysPerYear(accrualDaysPerYear);
        leaveType.setMaxCarryoverDays(maxCarryoverDays);
        return leaveTypeRepository.save(leaveType);
    }

    /** Suma días devengados al saldo corriente. Crea el saldo en cero si es la primera vez. */
    @Transactional
    public LeaveBalance accrue(Long employeeId, Long leaveTypeId, BigDecimal days) {
        TenantContext.require();
        LeaveBalance balance = findOrCreateBalance(employeeId, leaveTypeId);
        balance.setAccruedDays(balance.getAccruedDays().add(days));
        balance.setAvailableDays(balance.getAvailableDays().add(days));
        return leaveBalanceRepository.save(balance);
    }

    /**
     * Registra la solicitud. Si el tipo devenga saldo, exige disponibilidad —
     * no bloquea tipos sin devengo (licencia sin sueldo, por ejemplo).
     */
    @Transactional
    public LeaveRequest submit(Long employeeId, Long leaveTypeId, LocalDate startDate, LocalDate endDate,
                                BigDecimal days, String reason) {
        TenantContext.require();
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId).orElseThrow(
                () -> new IllegalArgumentException("Tipo de licencia " + leaveTypeId + " no existe en este tenant"));

        if (leaveType.getAccrualDaysPerYear() != null) {
            LeaveBalance balance = findOrCreateBalance(employeeId, leaveTypeId);
            if (balance.getAvailableDays().compareTo(days) < 0) {
                throw new IllegalArgumentException(
                        "El empleado no tiene saldo suficiente de '%s': disponible %s, solicitado %s"
                                .formatted(leaveType.getName(), balance.getAvailableDays(), days));
            }
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employeeId);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDays(days);
        request.setReason(reason);
        return leaveRequestRepository.save(request);
    }

    /** Aprueba y, si el tipo devenga saldo, descuenta los días de una vez. */
    @Transactional
    public LeaveRequest approve(Long requestId, Long approverUserId) {
        LeaveRequest request = transition(requestId, LeaveRequestStatus.APPROVED);
        request.setDecidedByUserId(approverUserId);
        request.setDecidedAt(Instant.now());

        if (request.getLeaveType().getAccrualDaysPerYear() != null) {
            LeaveBalance balance = findOrCreateBalance(request.getEmployeeId(), request.getLeaveType().getId());
            balance.setUsedDays(balance.getUsedDays().add(request.getDays()));
            balance.setAvailableDays(balance.getAvailableDays().subtract(request.getDays()));
            leaveBalanceRepository.save(balance);
        }
        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveRequest reject(Long requestId, Long approverUserId) {
        LeaveRequest request = transition(requestId, LeaveRequestStatus.REJECTED);
        request.setDecidedByUserId(approverUserId);
        request.setDecidedAt(Instant.now());
        return leaveRequestRepository.save(request);
    }

    /** Si la solicitud ya estaba aprobada (y por tanto ya descontó saldo), lo repone. */
    @Transactional
    public LeaveRequest cancel(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId).orElseThrow(
                () -> new IllegalArgumentException("Solicitud " + requestId + " no existe en este tenant"));
        boolean wasApproved = request.getStatus() == LeaveRequestStatus.APPROVED;

        request = transition(requestId, LeaveRequestStatus.CANCELLED);

        if (wasApproved && request.getLeaveType().getAccrualDaysPerYear() != null) {
            LeaveBalance balance = findOrCreateBalance(request.getEmployeeId(), request.getLeaveType().getId());
            balance.setUsedDays(balance.getUsedDays().subtract(request.getDays()));
            balance.setAvailableDays(balance.getAvailableDays().add(request.getDays()));
            leaveBalanceRepository.save(balance);
        }
        return leaveRequestRepository.save(request);
    }

    private LeaveRequest transition(Long requestId, LeaveRequestStatus target) {
        LeaveRequest request = leaveRequestRepository.findById(requestId).orElseThrow(
                () -> new IllegalArgumentException("Solicitud " + requestId + " no existe en este tenant"));
        LeaveRequestStatus current = request.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("LeaveRequest", requestId, current, target);
        }
        request.setStatus(target);
        return request;
    }

    private LeaveBalance findOrCreateBalance(Long employeeId, Long leaveTypeId) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(employeeId, leaveTypeId)
                .orElseGet(() -> {
                    LeaveBalance fresh = new LeaveBalance();
                    fresh.setEmployeeId(employeeId);
                    fresh.setLeaveType(leaveTypeRepository.getReferenceById(leaveTypeId));
                    return leaveBalanceRepository.save(fresh);
                });
    }
}
