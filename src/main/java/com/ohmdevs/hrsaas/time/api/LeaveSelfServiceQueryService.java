package com.ohmdevs.hrsaas.time.api;

import com.ohmdevs.hrsaas.time.application.HolidayCalendarLookup;
import com.ohmdevs.hrsaas.time.application.LeaveManagementService;
import com.ohmdevs.hrsaas.time.domain.LeaveBalance;
import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayCalendarRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveBalanceRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveRequestRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveTypeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Segundo contenido público de {@code time}, aparte de
 * {@link com.ohmdevs.hrsaas.time.api.TimeAndAttendanceSummaryService}: saldos
 * y solicitudes de licencia de <em>un solo empleado</em>, para el
 * autoservicio del portal (blueprint §09).
 *
 * <p>Todas las consultas y comandos reciben {@code employeeId} desde quien
 * llama ({@code portal}, que lo resuelve del actor autenticado) y lo aplican
 * como filtro — nunca confían en un id de solicitud sin comprobar antes que
 * pertenece a ese empleado ({@link #cancelOwnRequest}).
 */
@Service
public class LeaveSelfServiceQueryService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveManagementService leaveManagementService;

    public LeaveSelfServiceQueryService(LeaveTypeRepository leaveTypeRepository,
                                         LeaveBalanceRepository leaveBalanceRepository,
                                         LeaveRequestRepository leaveRequestRepository,
                                         HolidayCalendarRepository holidayCalendarRepository,
                                         HolidayRepository holidayRepository,
                                         LeaveManagementService leaveManagementService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayRepository = holidayRepository;
        this.leaveManagementService = leaveManagementService;
    }

    @Transactional
    public List<LeaveBalanceView> balancesFor(Long employeeId) {
        return leaveTypeRepository.findByActiveTrue().stream()
                .map(type -> toBalanceView(employeeId, type))
                .toList();
    }

    @Transactional
    public LeaveRequestPage requestsFor(Long employeeId, Long afterId, int limit) {
        Pageable page = PageRequest.of(0, limit + 1);
        List<LeaveRequest> rows = afterId == null
                ? leaveRequestRepository.findByEmployeeIdOrderByIdDesc(employeeId, page)
                : leaveRequestRepository.findByEmployeeIdAndIdLessThanOrderByIdDesc(employeeId, afterId, page);

        boolean hasMore = rows.size() > limit;
        List<LeaveRequest> pageRows = hasMore ? rows.subList(0, limit) : rows;
        Long nextAfterId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new LeaveRequestPage(pageRows.stream().map(LeaveSelfServiceQueryService::toView).toList(), nextAfterId);
    }

    /**
     * Calcula los días hábiles del rango (lunes a viernes, sin feriados de la
     * empresa) y delega en {@link LeaveManagementService#submit}. El cálculo
     * vive aquí, no en {@code portal}, porque necesita el calendario de
     * feriados — dato interno de {@code time}.
     */
    @Transactional
    public LeaveRequestView submitRequest(Long employeeId, Long companyId, String leaveTypeCode,
                                           LocalDate startDate, LocalDate endDate, String reason) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidLeaveRangeException("La fecha de fin no puede ser anterior a la de inicio");
        }
        LeaveType leaveType = leaveTypeRepository.findByCode(leaveTypeCode).orElseThrow(
                () -> new IllegalArgumentException("Tipo de licencia '" + leaveTypeCode + "' no existe en este tenant"));

        Set<LocalDate> holidays = HolidayCalendarLookup.companyWideHolidays(
                holidayCalendarRepository, holidayRepository, companyId, startDate, endDate);
        BigDecimal days = countBusinessDays(startDate, endDate, holidays);
        if (days.signum() <= 0) {
            throw new InvalidLeaveRangeException("El rango de fechas no contiene días hábiles");
        }

        LeaveRequest created = leaveManagementService.submit(employeeId, leaveType.getId(), startDate, endDate, days, reason);
        return toView(created);
    }

    /** Solo cancela si la solicitud es del empleado y sigue {@code PENDING} — ver Javadoc de las excepciones. */
    @Transactional
    public void cancelOwnRequest(Long employeeId, Long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .filter(candidate -> candidate.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw new LeaveRequestNotCancellableException(requestId, request.getStatus());
        }
        leaveManagementService.cancel(requestId);
    }

    private LeaveBalanceView toBalanceView(Long employeeId, LeaveType type) {
        if (type.getAccrualDaysPerYear() != null) {
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(employeeId, type.getId())
                    .orElse(null);
            BigDecimal taken = balance != null ? balance.getUsedDays() : BigDecimal.ZERO;
            BigDecimal available = balance != null ? balance.getAvailableDays() : BigDecimal.ZERO;
            return new LeaveBalanceView(type.getCode(), type.getName(), type.getAccrualDaysPerYear(), taken, available, null);
        }
        // Sin devengo (p. ej. licencia médica): no hay LeaveBalance que consultar, el
        // tipo se solicita y aprueba sin descontar saldo (LeaveManagementService.submit).
        // "takenDays" se aproxima sumando los días de solicitudes APROBADAS del año en
        // curso -- heurística de presentación para que el portal muestre algo, pendiente
        // de una política real si el negocio la necesita (p. ej. por año fiscal vs. calendario).
        BigDecimal takenThisYear = sumApprovedDaysThisYear(employeeId, type.getId());
        return new LeaveBalanceView(type.getCode(), type.getName(), null, takenThisYear, null, "Sin tope fijo definido para este tipo de licencia");
    }

    private BigDecimal sumApprovedDaysThisYear(Long employeeId, Long leaveTypeId) {
        int year = LocalDate.now().getYear();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        return leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, LeaveRequestStatus.APPROVED).stream()
                .filter(request -> request.getLeaveType().getId().equals(leaveTypeId))
                .filter(request -> !request.getStartDate().isAfter(yearEnd) && !request.getEndDate().isBefore(yearStart))
                .map(LeaveRequest::getDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal countBusinessDays(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        BigDecimal count = BigDecimal.ZERO;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DayOfWeek dayOfWeek = day.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY && !holidays.contains(day)) {
                count = count.add(BigDecimal.ONE);
            }
        }
        return count;
    }

    private static LeaveRequestView toView(LeaveRequest request) {
        return new LeaveRequestView(
                request.getId(), request.getLeaveType().getCode(), request.getLeaveType().getName(),
                request.getStartDate(), request.getEndDate(), request.getDays(), request.getStatus().name(),
                request.getReason(), request.getRequestedAt());
    }
}
