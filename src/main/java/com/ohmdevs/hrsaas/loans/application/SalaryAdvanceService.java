package com.ohmdevs.hrsaas.loans.application;

import com.ohmdevs.hrsaas.loans.domain.SalaryAdvance;
import com.ohmdevs.hrsaas.loans.domain.SalaryAdvanceStatus;
import com.ohmdevs.hrsaas.loans.infrastructure.SalaryAdvanceRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Avances de salario: solicitud, aprobación y marcado de descuento en nómina. */
@Service
public class SalaryAdvanceService {

    private final SalaryAdvanceRepository salaryAdvanceRepository;

    public SalaryAdvanceService(SalaryAdvanceRepository salaryAdvanceRepository) {
        this.salaryAdvanceRepository = salaryAdvanceRepository;
    }

    @Transactional
    public SalaryAdvance request(Long employeeId, BigDecimal amount, LocalDate requestDate) {
        TenantContext.require();
        SalaryAdvance advance = new SalaryAdvance();
        advance.setEmployeeId(employeeId);
        advance.setAmount(amount);
        advance.setRequestDate(requestDate);
        return salaryAdvanceRepository.save(advance);
    }

    @Transactional
    public SalaryAdvance approve(Long advanceId, LocalDate deductionDate) {
        SalaryAdvance advance = transition(advanceId, SalaryAdvanceStatus.APPROVED);
        advance.setDeductionDate(deductionDate);
        return salaryAdvanceRepository.save(advance);
    }

    @Transactional
    public SalaryAdvance reject(Long advanceId) {
        return transition(advanceId, SalaryAdvanceStatus.REJECTED);
    }

    /** Lo llama {@code payroll} (Fase 3) cuando la corrida que lo descontó queda cerrada. */
    @Transactional
    public SalaryAdvance markDeducted(Long advanceId) {
        return transition(advanceId, SalaryAdvanceStatus.DEDUCTED);
    }

    private SalaryAdvance transition(Long advanceId, SalaryAdvanceStatus target) {
        SalaryAdvance advance = salaryAdvanceRepository.findById(advanceId).orElseThrow(
                () -> new IllegalArgumentException("Avance " + advanceId + " no existe en este tenant"));
        SalaryAdvanceStatus current = advance.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("SalaryAdvance", advanceId, current, target);
        }
        advance.setStatus(target);
        return advance;
    }
}
