package com.ohmdevs.hrsaas.loans.api;

import com.ohmdevs.hrsaas.loans.domain.GarnishmentOrder;
import com.ohmdevs.hrsaas.loans.domain.GarnishmentStatus;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallment;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallmentStatus;
import com.ohmdevs.hrsaas.loans.domain.SalaryAdvance;
import com.ohmdevs.hrsaas.loans.domain.SalaryAdvanceStatus;
import com.ohmdevs.hrsaas.loans.infrastructure.GarnishmentOrderRepository;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanInstallmentRepository;
import com.ohmdevs.hrsaas.loans.infrastructure.SalaryAdvanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Único contenido público de {@code loans}: lo que {@code payroll} (Fase 3) necesita para un empleado y un período. */
@Service
public class PayrollDeductionSummaryService {

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final SalaryAdvanceRepository salaryAdvanceRepository;
    private final GarnishmentOrderRepository garnishmentOrderRepository;

    public PayrollDeductionSummaryService(LoanInstallmentRepository loanInstallmentRepository,
                                           SalaryAdvanceRepository salaryAdvanceRepository,
                                           GarnishmentOrderRepository garnishmentOrderRepository) {
        this.loanInstallmentRepository = loanInstallmentRepository;
        this.salaryAdvanceRepository = salaryAdvanceRepository;
        this.garnishmentOrderRepository = garnishmentOrderRepository;
    }

    @Transactional
    public PayrollDeductionSummary summarize(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        List<PayrollDeductionSummary.LoanInstallmentDue> loanInstallments =
                loanInstallmentRepository.findByLoanAgreement_EmployeeIdAndStatusAndDueDateBetween(
                                employeeId, LoanInstallmentStatus.PENDING, periodStart, periodEnd).stream()
                        .map(this::toLoanInstallmentDue)
                        .toList();

        List<PayrollDeductionSummary.SalaryAdvanceDue> salaryAdvances =
                salaryAdvanceRepository.findByEmployeeIdAndStatusAndDeductionDateBetween(
                                employeeId, SalaryAdvanceStatus.APPROVED, periodStart, periodEnd).stream()
                        .map(this::toSalaryAdvanceDue)
                        .toList();

        List<PayrollDeductionSummary.GarnishmentDue> garnishments =
                garnishmentOrderRepository.findByEmployeeIdAndStatusOrderByPriorityAsc(
                                employeeId, GarnishmentStatus.ACTIVE).stream()
                        .filter(order -> isActiveDuring(order, periodStart, periodEnd))
                        .map(this::toGarnishmentDue)
                        .toList();

        return new PayrollDeductionSummary(loanInstallments, salaryAdvances, garnishments);
    }

    private boolean isActiveDuring(GarnishmentOrder order, LocalDate periodStart, LocalDate periodEnd) {
        boolean startedInTime = !order.getStartDate().isAfter(periodEnd);
        boolean notEndedYet = order.getEndDate() == null || !order.getEndDate().isBefore(periodStart);
        return startedInTime && notEndedYet;
    }

    private PayrollDeductionSummary.LoanInstallmentDue toLoanInstallmentDue(LoanInstallment installment) {
        return new PayrollDeductionSummary.LoanInstallmentDue(
                installment.getLoanAgreement().getId(), installment.getId(), installment.getAmount());
    }

    private PayrollDeductionSummary.SalaryAdvanceDue toSalaryAdvanceDue(SalaryAdvance advance) {
        return new PayrollDeductionSummary.SalaryAdvanceDue(advance.getId(), advance.getAmount());
    }

    private PayrollDeductionSummary.GarnishmentDue toGarnishmentDue(GarnishmentOrder order) {
        return new PayrollDeductionSummary.GarnishmentDue(
                order.getId(), order.getPriority(), order.getFixedAmount(), order.getPercentage());
    }
}
