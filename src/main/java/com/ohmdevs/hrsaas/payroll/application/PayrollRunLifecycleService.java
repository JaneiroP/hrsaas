package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.contracts.api.ContractPayrollQueryService;
import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRunStatus;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * El ciclo de vida de una {@link PayrollRun} (AD-06, ver
 * {@link PayrollRunStatus}). Cada transición valida
 * {@code status.canTransitionTo(target)} aquí, nunca en el controlador ni en
 * la entidad — mismo estilo que {@code ContractLifecycleService}.
 *
 * <p><strong>AD-03</strong>: una corrida {@code CERRADA} no se edita jamás.
 * {@link #reverse(Long)} no reabre la original — crea una corrida nueva que
 * la referencia por {@code reversalOfRunId} y copia cada volante y cada
 * asiento de {@link Accrual} con el monto invertido.
 */
@Service
public class PayrollRunLifecycleService {

    private static final Set<PayrollRunStatus> REVERSIBLE = EnumSet.of(
            PayrollRunStatus.APROBADA, PayrollRunStatus.PAGADA, PayrollRunStatus.CERRADA);

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository payslipLineRepository;
    private final AccrualRepository accrualRepository;
    private final ContractPayrollQueryService contractPayrollQueryService;
    private final PayrollCalculationService payrollCalculationService;

    public PayrollRunLifecycleService(PayrollRunRepository payrollRunRepository,
                                       PayrollPeriodRepository payrollPeriodRepository,
                                       PayslipRepository payslipRepository,
                                       PayslipLineRepository payslipLineRepository,
                                       AccrualRepository accrualRepository,
                                       ContractPayrollQueryService contractPayrollQueryService,
                                       PayrollCalculationService payrollCalculationService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineRepository = payslipLineRepository;
        this.accrualRepository = accrualRepository;
        this.contractPayrollQueryService = contractPayrollQueryService;
        this.payrollCalculationService = payrollCalculationService;
    }

    @Transactional
    public PayrollRun createRun(Long companyId, Long payrollPeriodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new IllegalArgumentException("PayrollPeriod no encontrado: " + payrollPeriodId));
        PayrollRun run = new PayrollRun();
        run.setPayrollPeriod(period);
        run.setCompanyId(companyId);
        run.setStatus(PayrollRunStatus.BORRADOR);
        return payrollRunRepository.save(run);
    }

    /** {@code BORRADOR → CALCULADA}: calcula un volante por cada contrato activo de la empresa. */
    @Transactional
    public PayrollRun calculate(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.CALCULADA);

        PayrollPeriod period = run.getPayrollPeriod();
        List<ActiveContractSummary> contracts = contractPayrollQueryService.activeContractsForCompany(run.getCompanyId());
        for (ActiveContractSummary contract : contracts) {
            payrollCalculationService.calculateForContract(run, contract, period.getStartDate(), period.getEndDate());
        }

        run.setCalculatedAt(Instant.now());
        run.setConfigVersionId(Instant.now().toString());
        return payrollRunRepository.save(run);
    }

    @Transactional
    public PayrollRun submitForReview(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.EN_REVISION);
        return payrollRunRepository.save(run);
    }

    /** {@code EN_REVISION → BORRADOR}: descarta lo calculado para permitir recalcular. */
    @Transactional
    public PayrollRun returnToDraft(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.BORRADOR);
        clearCalculatedData(run.getId());
        run.setCalculatedAt(null);
        return payrollRunRepository.save(run);
    }

    @Transactional
    public PayrollRun approve(Long runId, Long approvedByUserId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.APROBADA);
        run.setApprovedAt(Instant.now());
        run.setApprovedByUserId(approvedByUserId);
        return payrollRunRepository.save(run);
    }

    @Transactional
    public PayrollRun markPaid(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.PAGADA);
        run.setPaidAt(Instant.now());
        return payrollRunRepository.save(run);
    }

    @Transactional
    public PayrollRun close(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.CERRADA);
        run.setClosedAt(Instant.now());
        return payrollRunRepository.save(run);
    }

    /** Solo legal antes de {@code APROBADA} — después, la corrección es {@link #reverse(Long)}. */
    @Transactional
    public PayrollRun cancel(Long runId) {
        PayrollRun run = getRun(runId);
        transition(run, PayrollRunStatus.ANULADA);
        return payrollRunRepository.save(run);
    }

    @Transactional
    public PayrollRun reverse(Long originalRunId) {
        PayrollRun original = getRun(originalRunId);
        if (!REVERSIBLE.contains(original.getStatus())) {
            throw new IllegalStateTransitionException("PayrollRun.reverse", original.getId(),
                    original.getStatus(), PayrollRunStatus.CALCULADA);
        }

        PayrollRun reversal = new PayrollRun();
        reversal.setPayrollPeriod(original.getPayrollPeriod());
        reversal.setCompanyId(original.getCompanyId());
        reversal.setStatus(PayrollRunStatus.CALCULADA);
        reversal.setReversalOfRunId(original.getId());
        reversal.setCalculatedAt(Instant.now());
        reversal = payrollRunRepository.save(reversal);

        for (Payslip originalPayslip : payslipRepository.findByPayrollRunId(original.getId())) {
            copyReversedPayslip(reversal, originalPayslip);
        }
        for (Accrual originalAccrual : accrualRepository.findByPayrollRunId(original.getId())) {
            copyReversedAccrual(reversal, originalAccrual);
        }
        return reversal;
    }

    private void copyReversedPayslip(PayrollRun reversal, Payslip originalPayslip) {
        Payslip reversedPayslip = new Payslip();
        reversedPayslip.setPayrollRun(reversal);
        reversedPayslip.setEmployeeId(originalPayslip.getEmployeeId());
        reversedPayslip.setContractId(originalPayslip.getContractId());
        reversedPayslip.setCurrency(originalPayslip.getCurrency());
        reversedPayslip.setGrossPay(originalPayslip.getGrossPay().negate());
        reversedPayslip.setNetPay(originalPayslip.getNetPay().negate());
        reversedPayslip = payslipRepository.save(reversedPayslip);

        for (PayslipLine originalLine : payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(originalPayslip.getId())) {
            PayslipLine reversedLine = new PayslipLine();
            reversedLine.setPayslip(reversedPayslip);
            reversedLine.setPayrollConceptId(originalLine.getPayrollConceptId());
            reversedLine.setDescription("Reverso: " + originalLine.getDescription());
            reversedLine.setType(originalLine.getType());
            reversedLine.setAmount(originalLine.getAmount().negate());
            reversedLine.setLineOrder(originalLine.getLineOrder());
            payslipLineRepository.save(reversedLine);
        }
    }

    private void copyReversedAccrual(PayrollRun reversal, Accrual originalAccrual) {
        Accrual reversedAccrual = new Accrual();
        reversedAccrual.setPayrollRun(reversal);
        reversedAccrual.setEmployeeId(originalAccrual.getEmployeeId());
        reversedAccrual.setConceptCode(originalAccrual.getConceptCode());
        reversedAccrual.setType(originalAccrual.getType());
        reversedAccrual.setAmount(originalAccrual.getAmount().negate());
        accrualRepository.save(reversedAccrual);
    }

    private void clearCalculatedData(Long runId) {
        for (Payslip payslip : payslipRepository.findByPayrollRunId(runId)) {
            payslipLineRepository.deleteAll(payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(payslip.getId()));
        }
        payslipRepository.deleteAll(payslipRepository.findByPayrollRunId(runId));
        accrualRepository.deleteAll(accrualRepository.findByPayrollRunId(runId));
    }

    private PayrollRun getRun(Long runId) {
        return payrollRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("PayrollRun no encontrado: " + runId));
    }

    private void transition(PayrollRun run, PayrollRunStatus target) {
        if (!run.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException("PayrollRun", run.getId(), run.getStatus(), target);
        }
        run.setStatus(target);
    }
}
