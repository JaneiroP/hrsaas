package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.CostCenter;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El resumen de nómina exportado en CSV (blueprint §12 Fase 4) coincide con lo que el motor calculó. */
class PayrollRunExportServiceTest extends AbstractPostgresTest {

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
    private PayrollRunLifecycleService payrollRunLifecycleService;
    @Autowired
    private PayrollRunExportService payrollRunExportService;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private AccrualRepository accrualRepository;

    private Long tenantId;

    @Test
    @DisplayName("el CSV exportado tiene cabecera y un renglon por volante con bruto, neto y descuentos")
    void exportedCsvMatchesTheCalculatedPayslip() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        Long calculatedRunId = TenantContext.callWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            var employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            contracts.hire(employee.getId(), company.getId(), position.getId(), null, null,
                    ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("50000.00"), "DOP");

            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 1, 1));
            period.setEndDate(LocalDate.of(2025, 1, 31));
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);

            PayrollRun created = payrollRunLifecycleService.createRun(company.getId(), period.getId());
            PayrollRun calculated = payrollRunLifecycleService.calculate(created.getId());
            return calculated.getId();
        });

        byte[] csvBytes = TenantContext.callWith(tenantId,
                () -> payrollRunExportService.exportPayrollRunSummaryCsv(calculatedRunId));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("employeeId,contractId,currency,grossPay,netPay,totalDeductions");
        assertThat(csv)
                .as("sin conceptos configurados, el volante es solo el salario base: bruto = neto = 50000.00, sin descuentos")
                .contains("DOP,50000.00,50000.00,0.00");
    }

    @Test
    @DisplayName("exportar una corrida sin volantes calculados es ilegal")
    void exportingAnUncalculatedRunIsRejected() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        Long draftRunId = TenantContext.callWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 1, 1));
            period.setEndDate(LocalDate.of(2025, 1, 31));
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);
            return payrollRunLifecycleService.createRun(company.getId(), period.getId()).getId();
        });

        assertThatThrownBy(() -> TenantContext.callWith(tenantId,
                () -> payrollRunExportService.exportPayrollRunSummaryCsv(draftRunId)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("costo laboral y resumen por centro de costo suman el bruto mas los aportes patronales, no solo el neto")
    void laborCostAndCostCenterSummaryIncludeEmployerAccruals() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        record Setup(Long runId, Long costCenterId) {
        }

        Setup setup = TenantContext.callWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            CostCenter costCenter = orgStructure.createCostCenter(company, "CC-01", "Ingenieria");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            var employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            contracts.hire(employee.getId(), company.getId(), position.getId(), costCenter.getId(), null,
                    ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("50000.00"), "DOP");

            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 1, 1));
            period.setEndDate(LocalDate.of(2025, 1, 31));
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);

            PayrollRun createdRun = payrollRunLifecycleService.createRun(company.getId(), period.getId());
            PayrollRun calculated = payrollRunLifecycleService.calculate(createdRun.getId());

            // El motor no calculo ningun aporte patronal (sin PayrollConcept configurado) -- se
            // siembra directo para probar que la exportacion lo suma, sin re-probar el motor entero.
            Payslip payslip = payslipRepository.findByPayrollRunId(calculated.getId()).get(0);
            Accrual employerContribution = new Accrual();
            employerContribution.setPayrollRun(calculated);
            employerContribution.setEmployeeId(payslip.getEmployeeId());
            employerContribution.setConceptCode("SFS_PATRONAL");
            employerContribution.setType(PayrollConceptType.EMPLOYER_CONTRIBUTION);
            employerContribution.setAmount(new BigDecimal("3545.00"));
            accrualRepository.save(employerContribution);

            return new Setup(calculated.getId(), costCenter.getId());
        });

        String laborCostCsv = new String(TenantContext.callWith(tenantId,
                () -> payrollRunExportService.exportLaborCostCsv(setup.runId())), StandardCharsets.UTF_8);
        assertThat(laborCostCsv).startsWith("employeeId,contractId,currency,grossPay,employerAccruals,totalLaborCost");
        assertThat(laborCostCsv)
                .as("costo laboral = bruto (50000.00) + aporte patronal (3545.00) = 53545.00")
                .contains("DOP,50000.00,3545.00,53545.00");

        String costCenterCsv = new String(TenantContext.callWith(tenantId,
                () -> payrollRunExportService.exportCostCenterSummaryCsv(setup.runId())), StandardCharsets.UTF_8);
        assertThat(costCenterCsv).startsWith("costCenterId,employeeCount,totalGrossPay,totalNetPay,totalEmployerAccruals,totalLaborCost");
        assertThat(costCenterCsv)
                .as("un empleado en CC-01: mismo total de costo laboral agregado por centro de costo")
                .contains(setup.costCenterId() + ",1,50000.00,50000.00,3545.00,53545.00");
    }
}
