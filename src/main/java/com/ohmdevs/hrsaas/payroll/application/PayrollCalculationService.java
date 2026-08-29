package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.loans.api.PayrollDeductionSummary;
import com.ohmdevs.hrsaas.loans.api.PayrollDeductionSummaryService;
import com.ohmdevs.hrsaas.payroll.application.strategy.ConceptCalculationStrategy;
import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollConceptRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.shared.money.Money;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;
import com.ohmdevs.hrsaas.time.api.TimeAndAttendanceSummaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * El cálculo por empleado: la tubería de 8 pasos del blueprint (§04) —
 * devengos fijos → devengos variables → base cotizable → TSS empleado →
 * base ISR → ISR retenido → deducciones → NETO — más aportes patronales y
 * provisiones en paralelo, sin tocar el neto.
 *
 * <p>El <strong>orden</strong> de la tubería está fijo aquí, en Java — lo
 * que es configurable por tenant sin desplegar es <em>qué</em> conceptos
 * corren en cada tramo y <em>cómo</em> se calculan (vía
 * {@link PayrollConcept}/{@link ConceptCalculationStrategy}), no la
 * secuencia de pasos en sí (decisión documentada en
 * {@code payroll/package-info.java}).
 */
@Service
public class PayrollCalculationService {

    private final Map<com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy, ConceptCalculationStrategy> strategies;
    private final PayrollConceptRepository payrollConceptRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository payslipLineRepository;
    private final AccrualRepository accrualRepository;
    private final TimeAndAttendanceSummaryService timeAndAttendanceSummaryService;
    private final PayrollDeductionSummaryService payrollDeductionSummaryService;

    public PayrollCalculationService(List<ConceptCalculationStrategy> strategyBeans,
                                      PayrollConceptRepository payrollConceptRepository,
                                      PayslipRepository payslipRepository,
                                      PayslipLineRepository payslipLineRepository,
                                      AccrualRepository accrualRepository,
                                      TimeAndAttendanceSummaryService timeAndAttendanceSummaryService,
                                      PayrollDeductionSummaryService payrollDeductionSummaryService) {
        this.strategies = strategyBeans.stream()
                .collect(java.util.stream.Collectors.toMap(ConceptCalculationStrategy::handles, Function.identity()));
        this.payrollConceptRepository = payrollConceptRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineRepository = payslipLineRepository;
        this.accrualRepository = accrualRepository;
        this.timeAndAttendanceSummaryService = timeAndAttendanceSummaryService;
        this.payrollDeductionSummaryService = payrollDeductionSummaryService;
    }

    @Transactional
    public Payslip calculateForContract(PayrollRun run, ActiveContractSummary contract,
                                         LocalDate periodStart, LocalDate periodEnd) {
        AttendanceSummary attendance = timeAndAttendanceSummaryService.summarize(
                contract.employeeId(), contract.companyId(), periodStart, periodEnd);
        PayrollDeductionSummary deductionSummary = payrollDeductionSummaryService.summarize(
                contract.employeeId(), periodStart, periodEnd);
        PayrollCalculationContext context = new PayrollCalculationContext(contract, attendance, periodStart, periodEnd);
        List<PayrollConcept> activeConcepts = payrollConceptRepository.findByActiveTrueOrderByExecutionOrderAsc();

        Payslip payslip = new Payslip();
        payslip.setPayrollRun(run);
        payslip.setEmployeeId(contract.employeeId());
        payslip.setContractId(contract.contractId());
        payslip.setCurrency(contract.currency());
        payslip.setGrossPay(BigDecimal.ZERO.setScale(Money.SCALE));
        payslip.setNetPay(BigDecimal.ZERO.setScale(Money.SCALE));
        payslip = payslipRepository.save(payslip);

        int order = 0;

        // Paso 1: salario base — estructural, viene de contracts, no de un PayrollConcept.
        BigDecimal baseSalary = contract.currentSalary().setScale(Money.INTERMEDIATE_SCALE);
        context.recordEarning(baseSalary, true);
        order = addLine(payslip, null, "Salario base", PayrollConceptType.EARNING, baseSalary, order);

        // Pasos 1-2: devengos configurados (fijos y variables), en el orden del tenant.
        for (PayrollConcept concept : activeConcepts) {
            if (concept.getType() != PayrollConceptType.EARNING) {
                continue;
            }
            BigDecimal amount = dispatch(concept, context, periodEnd);
            context.recordEarning(amount, concept.isTaxableForIsr());
            order = addLine(payslip, concept.getId(), concept.getName(), PayrollConceptType.EARNING, amount, order);
        }

        // Paso 3 (base cotizable): no es una línea, es context.base("GROSS") — cada RATE_OF_BASE aplica su propio tope.
        // Paso 4: TSS a cargo del empleado.
        for (PayrollConcept concept : activeConcepts) {
            if (concept.getType() != PayrollConceptType.DEDUCTION || !concept.isContributesToTss()) {
                continue;
            }
            BigDecimal amount = dispatch(concept, context, periodEnd);
            context.recordTssEmployeeDeduction(amount);
            order = addLine(payslip, concept.getId(), concept.getName(), PayrollConceptType.DEDUCTION, amount.negate(), order);
        }

        // Paso 5 (base ISR): context.base("ISR_TAXABLE_BASE"), ya disponible tras el paso 4.
        // Paso 6: ISR retenido.
        for (PayrollConcept concept : activeConcepts) {
            if (concept.getType() != PayrollConceptType.DEDUCTION
                    || concept.getFormulaStrategy() != com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy.BRACKET_TABLE) {
                continue;
            }
            BigDecimal amount = dispatch(concept, context, periodEnd);
            order = addLine(payslip, concept.getId(), concept.getName(), PayrollConceptType.DEDUCTION, amount.negate(), order);
        }

        // Paso 7: otras deducciones — conceptos configurados que no son TSS ni ISR...
        for (PayrollConcept concept : activeConcepts) {
            if (concept.getType() != PayrollConceptType.DEDUCTION
                    || concept.isContributesToTss()
                    || concept.getFormulaStrategy() == com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy.BRACKET_TABLE) {
                continue;
            }
            BigDecimal amount = dispatch(concept, context, periodEnd);
            order = addLine(payslip, concept.getId(), concept.getName(), PayrollConceptType.DEDUCTION, amount.negate(), order);
        }
        // ...y las de loans :: api (préstamos, avances, embargos), que no vienen de un PayrollConcept.
        order = addLoanDeductions(payslip, deductionSummary, order);
        order = addSalaryAdvanceDeductions(payslip, deductionSummary, order);
        addGarnishmentDeductions(payslip, deductionSummary, context, order);

        // Aportes patronales y provisiones: Accrual aparte, nunca afectan el neto.
        for (PayrollConcept concept : activeConcepts) {
            if (concept.getType() != PayrollConceptType.EMPLOYER_CONTRIBUTION && concept.getType() != PayrollConceptType.PROVISION) {
                continue;
            }
            BigDecimal amount = dispatch(concept, context, periodEnd);
            Accrual accrual = new Accrual();
            accrual.setPayrollRun(run);
            accrual.setEmployeeId(contract.employeeId());
            accrual.setConceptCode(concept.getCode());
            accrual.setType(concept.getType());
            accrual.setAmount(Money.of(amount, contract.currency()).amount());
            accrualRepository.save(accrual);
        }

        // Paso 8: NETO — se recalcula desde las líneas ya persistidas, nunca acumulado a mano en paralelo.
        finalizePayslip(payslip);
        return payslip;
    }

    private int addLoanDeductions(Payslip payslip, PayrollDeductionSummary deductionSummary, int order) {
        for (var due : deductionSummary.loanInstallments()) {
            order = addLine(payslip, null, "Cuota de préstamo #" + due.loanAgreementId(),
                    PayrollConceptType.DEDUCTION, due.amount().negate(), order);
        }
        return order;
    }

    private int addSalaryAdvanceDeductions(Payslip payslip, PayrollDeductionSummary deductionSummary, int order) {
        for (var due : deductionSummary.salaryAdvances()) {
            order = addLine(payslip, null, "Avance de salario #" + due.salaryAdvanceId(),
                    PayrollConceptType.DEDUCTION, due.amount().negate(), order);
        }
        return order;
    }

    /**
     * Embargo porcentual: aplicado sobre {@code GROSS}, no sobre un "neto
     * embargable" legalmente preciso (mínimo inembargable, prelación entre
     * varios embargos compitiendo por el mismo neto) — simplificación
     * deliberada, señalada como pendiente de validar con un abogado
     * laboral/contador antes de nómina real (AD-04: no se inventa la regla
     * fina, se deja explícita la que sí se implementó).
     */
    private void addGarnishmentDeductions(Payslip payslip, PayrollDeductionSummary deductionSummary,
                                           PayrollCalculationContext context, int order) {
        for (var due : deductionSummary.garnishments()) {
            BigDecimal amount = due.fixedAmount() != null
                    ? due.fixedAmount()
                    : context.base("GROSS").multiply(due.percentage());
            order = addLine(payslip, null, "Embargo #" + due.garnishmentOrderId(),
                    PayrollConceptType.DEDUCTION, amount.negate(), order);
        }
    }

    private BigDecimal dispatch(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate) {
        ConceptCalculationStrategy strategy = strategies.get(concept.getFormulaStrategy());
        if (strategy == null) {
            throw new IllegalStateException("Sin ConceptCalculationStrategy para " + concept.getFormulaStrategy());
        }
        return strategy.calculate(concept, context, asOfDate);
    }

    private int addLine(Payslip payslip, Long conceptId, String description, PayrollConceptType type,
                         BigDecimal signedRawAmount, int order) {
        Money rounded = Money.of(signedRawAmount, payslip.getCurrency());
        PayslipLine line = new PayslipLine();
        line.setPayslip(payslip);
        line.setPayrollConceptId(conceptId);
        line.setDescription(description);
        line.setType(type);
        line.setAmount(rounded.amount());
        line.setLineOrder(order);
        payslipLineRepository.save(line);
        return order + 1;
    }

    private void finalizePayslip(Payslip payslip) {
        List<PayslipLine> lines = payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(payslip.getId());
        BigDecimal gross = lines.stream()
                .filter(line -> line.getType() == PayrollConceptType.EARNING)
                .map(PayslipLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = lines.stream()
                .map(PayslipLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        payslip.setGrossPay(gross.setScale(Money.SCALE));
        payslip.setNetPay(net.setScale(Money.SCALE));
        payslipRepository.save(payslip);
    }
}
