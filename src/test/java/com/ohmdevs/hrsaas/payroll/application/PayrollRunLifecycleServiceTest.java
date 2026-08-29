package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRunStatus;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollConceptRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import com.ohmdevs.hrsaas.taxconfig.domain.StatutoryParameter;
import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.ContributionSchemeRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.StatutoryParameterRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.TaxBracketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El test central de {@code payroll}, estilo "golden file" — pero
 * <strong>explícitamente ilustrativo, no validado por un contador
 * dominicano</strong> (AD-04): las tasas de TSS, el multiplicador de horas
 * extra, y la escala de ISR de este test son valores inventados solo para
 * verificar que la tubería de 8 pasos (blueprint §04) suma exacto, no
 * cifras reales de SFS/AFP/DGII. El juego de golden files real, acordado
 * con un contador, queda fuera del alcance de esta pasada — ver
 * {@code payroll/package-info.java}.
 */
class PayrollRunLifecycleServiceTest extends AbstractPostgresTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 1, 31);

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private ContractLifecycleService contracts;
    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;
    @Autowired
    private PayrollConceptRepository payrollConceptRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private PayslipLineRepository payslipLineRepository;
    @Autowired
    private AccrualRepository accrualRepository;
    @Autowired
    private PayrollRunLifecycleService payrollRunLifecycleService;
    @Autowired
    private ContributionSchemeRepository contributionSchemeRepository;
    @Autowired
    private TaxBracketRepository taxBracketRepository;
    @Autowired
    private StatutoryParameterRepository statutoryParameterRepository;

    private Long tenantId;
    private Company company;
    private Employee employee;
    private PayrollPeriod period;
    private String sfsCode;
    private String afpCode;
    private String isrCode;
    private String overtimeMultiplierParam;
    private String standardMonthlyHoursParam;

    @BeforeEach
    void provisionTenantCompanyEmployeeContractAndTestOnlyTaxConfig() {
        // taxconfig no es por tenant (sin RLS, ver su package-info): los codigos
        // llevan un sufijo unico por corrida de test para no colisionar con el
        // indice parcial "a lo sumo una fila abierta por codigo" entre metodos.
        String run = UUID.randomUUID().toString().substring(0, 8);
        sfsCode = "SFS_TEST_" + run;
        afpCode = "AFP_TEST_" + run;
        isrCode = "ISR_TEST_" + run;
        overtimeMultiplierParam = "RECARGO_HORAS_EXTRA_DIURNAS_TEST_" + run;
        standardMonthlyHoursParam = "HORAS_MES_ESTANDAR_TEST_" + run;

        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        seedTestOnlyTaxConfig();

        TenantContext.runWith(tenantId, () -> {
            company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            contracts.hire(employee.getId(), company.getId(), position.getId(), null, null,
                    ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("50000.00"), "DOP");

            period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(PERIOD_START);
            period.setEndDate(PERIOD_END);
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);

            seedPayrollConcepts();
        });
    }

    @Test
    @DisplayName("calculate produce un volante cuyas lineas cuadran al centavo con la tuberia de 8 pasos")
    void calculateProducesTheExpectedPayslipForASingleEmployee() {
        PayrollRun run = TenantContext.callWith(tenantId,
                () -> payrollRunLifecycleService.createRun(company.getId(), period.getId()));
        assertThat(run.getStatus()).isEqualTo(PayrollRunStatus.BORRADOR);

        PayrollRun calculated = TenantContext.callWith(tenantId, () -> payrollRunLifecycleService.calculate(run.getId()));
        assertThat(calculated.getStatus()).isEqualTo(PayrollRunStatus.CALCULADA);
        assertThat(calculated.getCalculatedAt()).isNotNull();

        List<Payslip> payslips = TenantContext.callWith(tenantId, () -> payslipRepository.findByPayrollRunId(calculated.getId()));
        assertThat(payslips).hasSize(1);
        Payslip payslip = payslips.get(0);

        // Bruto = salario base (50000) + bono transporte fijo (2000) + horas extra (0, sin marcajes en el periodo).
        assertThat(payslip.getGrossPay()).isEqualByComparingTo("52000.00");
        // Neto = bruto - TSS empleado (SFS 1580.80 + AFP 1492.40) - ISR (6885.36) = 42041.44.
        assertThat(payslip.getNetPay()).isEqualByComparingTo("42041.44");

        List<PayslipLine> lines = TenantContext.callWith(tenantId,
                () -> payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(payslip.getId()));
        assertThat(lines.stream().map(PayslipLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("la suma de todas las lineas debe coincidir exacto con el neto del volante, sin logica aparte")
                .isEqualByComparingTo(payslip.getNetPay());

        assertThat(lineAmount(lines, "Salario base")).isEqualByComparingTo("50000.00");
        assertThat(lineAmount(lines, "Bono de transporte")).isEqualByComparingTo("2000.00");
        assertThat(lineAmount(lines, "SFS empleado")).isEqualByComparingTo("-1580.80");
        assertThat(lineAmount(lines, "AFP empleado")).isEqualByComparingTo("-1492.40");
        assertThat(lineAmount(lines, "ISR retenido")).isEqualByComparingTo("-6885.36");

        var accruals = TenantContext.callWith(tenantId, () -> accrualRepository.findByPayrollRunId(calculated.getId()));
        assertThat(accruals).hasSize(2);
        assertThat(accruals.stream().map(a -> a.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("SFS patronal (3686.80) + AFP patronal (3692.00)")
                .isEqualByComparingTo("7378.80");
    }

    @Test
    @DisplayName("el ciclo completo BORRADOR -> CALCULADA -> EN_REVISION -> APROBADA -> PAGADA -> CERRADA es legal")
    void fullLifecycleFollowsTheStateMachineToClosed() {
        Long runId = TenantContext.callWith(tenantId,
                () -> payrollRunLifecycleService.createRun(company.getId(), period.getId()).getId());

        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.calculate(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.submitForReview(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.approve(runId, 999L));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.markPaid(runId));
        PayrollRun closed = TenantContext.callWith(tenantId, () -> payrollRunLifecycleService.close(runId));

        assertThat(closed.getStatus()).isEqualTo(PayrollRunStatus.CERRADA);
        assertThat(closed.getApprovedByUserId()).isEqualTo(999L);
        assertThat(closed.getPaidAt()).isNotNull();
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelar una corrida ya aprobada es ilegal: AD-03 exige un reverso, no una anulacion retroactiva")
    void cancellingAfterApprovalIsRejected() {
        Long runId = TenantContext.callWith(tenantId,
                () -> payrollRunLifecycleService.createRun(company.getId(), period.getId()).getId());
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.calculate(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.submitForReview(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.approve(runId, 1L));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.cancel(runId)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("reverse crea una corrida nueva que no toca la original y cuyos montos netos a cero combinados")
    void reverseCreatesACompensatingRunThatNetsToZero() {
        Long runId = TenantContext.callWith(tenantId,
                () -> payrollRunLifecycleService.createRun(company.getId(), period.getId()).getId());
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.calculate(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.submitForReview(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.approve(runId, 1L));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.markPaid(runId));
        TenantContext.runWith(tenantId, () -> payrollRunLifecycleService.close(runId));

        PayrollRun reversal = TenantContext.callWith(tenantId, () -> payrollRunLifecycleService.reverse(runId));

        assertThat(reversal.getId()).isNotEqualTo(runId);
        assertThat(reversal.getReversalOfRunId()).isEqualTo(runId);
        assertThat(reversal.getStatus()).isEqualTo(PayrollRunStatus.CALCULADA);

        // La original sigue CERRADA e intacta (AD-03): nunca se edita.
        List<Payslip> originalPayslips = TenantContext.callWith(tenantId, () -> payslipRepository.findByPayrollRunId(runId));
        List<Payslip> reversalPayslips = TenantContext.callWith(tenantId, () -> payslipRepository.findByPayrollRunId(reversal.getId()));
        assertThat(originalPayslips).hasSize(1);
        assertThat(reversalPayslips).hasSize(1);
        assertThat(originalPayslips.get(0).getNetPay().add(reversalPayslips.get(0).getNetPay()))
                .as("volante original + volante de reverso deben netear exactamente a cero")
                .isEqualByComparingTo("0.00");
    }

    private BigDecimal lineAmount(List<PayslipLine> lines, String description) {
        return lines.stream()
                .filter(line -> line.getDescription().equals(description))
                .map(PayslipLine::getAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sin linea '" + description + "' en el volante"));
    }

    /**
     * {@code taxconfig} no es por tenant (sin RLS, ver su {@code package-info}):
     * estas filas quedan visibles para cualquier tenant que corra este test en
     * la misma base de Testcontainers, con codigos {@code _TEST} que no
     * colisionan con codigos reales.
     */
    private void seedTestOnlyTaxConfig() {
        contributionSchemeRepository.save(scheme(sfsCode, "0.0304", "0.0709", "100000.00"));
        contributionSchemeRepository.save(scheme(afpCode, "0.0287", "0.0710", "100000.00"));

        taxBracketRepository.save(bracket(isrCode, 1, "0.00", "10000.00", "0.0000"));
        taxBracketRepository.save(bracket(isrCode, 2, "10000.00", "20000.00", "0.1500"));
        taxBracketRepository.save(bracket(isrCode, 3, "20000.00", null, "0.2000"));

        statutoryParameterRepository.save(parameter(overtimeMultiplierParam, "1.3500"));
        statutoryParameterRepository.save(parameter(standardMonthlyHoursParam, "195.0000"));
    }

    private void seedPayrollConcepts() {
        payrollConceptRepository.save(concept("BONO_TRANSPORTE", "Bono de transporte", PayrollConceptType.EARNING,
                10, false, false, FormulaStrategy.FIXED, "{\"amount\": \"2000.00\"}"));
        payrollConceptRepository.save(concept("HORAS_EXTRA", "Horas extra", PayrollConceptType.EARNING,
                20, true, false, FormulaStrategy.HOURS_TIMES_RATE,
                "{\"rateMultiplierParameterCode\": \"" + overtimeMultiplierParam + "\", "
                        + "\"standardMonthlyHoursParameterCode\": \"" + standardMonthlyHoursParam + "\"}"));
        payrollConceptRepository.save(concept("SFS_EMPLEADO", "SFS empleado", PayrollConceptType.DEDUCTION,
                30, false, true, FormulaStrategy.RATE_OF_BASE,
                "{\"schemeCode\": \"" + sfsCode + "\", \"ratePerspective\": \"EMPLOYEE\", \"base\": \"GROSS\"}"));
        payrollConceptRepository.save(concept("AFP_EMPLEADO", "AFP empleado", PayrollConceptType.DEDUCTION,
                31, false, true, FormulaStrategy.RATE_OF_BASE,
                "{\"schemeCode\": \"" + afpCode + "\", \"ratePerspective\": \"EMPLOYEE\", \"base\": \"GROSS\"}"));
        payrollConceptRepository.save(concept("ISR", "ISR retenido", PayrollConceptType.DEDUCTION,
                40, false, false, FormulaStrategy.BRACKET_TABLE,
                "{\"schemeCode\": \"" + isrCode + "\", \"base\": \"ISR_TAXABLE_BASE\"}"));
        payrollConceptRepository.save(concept("SFS_PATRONAL", "SFS patronal", PayrollConceptType.EMPLOYER_CONTRIBUTION,
                50, false, false, FormulaStrategy.RATE_OF_BASE,
                "{\"schemeCode\": \"" + sfsCode + "\", \"ratePerspective\": \"EMPLOYER\", \"base\": \"GROSS\"}"));
        payrollConceptRepository.save(concept("AFP_PATRONAL", "AFP patronal", PayrollConceptType.EMPLOYER_CONTRIBUTION,
                51, false, false, FormulaStrategy.RATE_OF_BASE,
                "{\"schemeCode\": \"" + afpCode + "\", \"ratePerspective\": \"EMPLOYER\", \"base\": \"GROSS\"}"));
    }

    private ContributionScheme scheme(String code, String employeeRate, String employerRate, String cap) {
        ContributionScheme scheme = new ContributionScheme();
        scheme.setCode(code);
        scheme.setName(code);
        scheme.setEmployeeRate(new BigDecimal(employeeRate));
        scheme.setEmployerRate(new BigDecimal(employerRate));
        scheme.setCapAmount(new BigDecimal(cap));
        scheme.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return scheme;
    }

    private TaxBracket bracket(String schemeCode, int order, String lower, String upper, String rate) {
        TaxBracket bracket = new TaxBracket();
        bracket.setSchemeCode(schemeCode);
        bracket.setBracketOrder(order);
        bracket.setLowerBound(new BigDecimal(lower));
        bracket.setUpperBound(upper == null ? null : new BigDecimal(upper));
        bracket.setRate(new BigDecimal(rate));
        bracket.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return bracket;
    }

    private StatutoryParameter parameter(String code, String value) {
        StatutoryParameter parameter = new StatutoryParameter();
        parameter.setCode(code);
        parameter.setDescription(code);
        parameter.setValue(new BigDecimal(value));
        parameter.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return parameter;
    }

    private PayrollConcept concept(String code, String name, PayrollConceptType type, int order,
                                    boolean taxableForIsr, boolean contributesToTss,
                                    FormulaStrategy formulaStrategy, String formulaParams) {
        PayrollConcept concept = new PayrollConcept();
        concept.setCode(code);
        concept.setName(name);
        concept.setType(type);
        concept.setExecutionOrder(order);
        concept.setTaxableForIsr(taxableForIsr);
        concept.setContributesToTss(contributesToTss);
        concept.setAffectsRegalia(false);
        concept.setFormulaStrategy(formulaStrategy);
        concept.setFormulaParams(formulaParams);
        concept.setActive(true);
        return concept;
    }
}
