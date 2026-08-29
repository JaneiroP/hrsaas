package com.ohmdevs.hrsaas.payroll;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado de {@code payroll} (AD-02) — a diferencia de
 * {@code taxconfig} (sin {@code tenant_id}, ver su {@code package-info}),
 * las seis tablas de este módulo sí son datos de negocio del tenant y llevan
 * RLS estándar (V20), así que se prueban con el mismo patrón que el resto de
 * la suite: repositorios de Spring Data bajo {@link TenantContext}.
 */
class PayrollIsolationTest extends AbstractPostgresTest {

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
    private PayrollRunRepository payrollRunRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private PayslipLineRepository payslipLineRepository;
    @Autowired
    private AccrualRepository accrualRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private PayrollRun acmeRun;
    private Payslip acmePayslip;

    @BeforeEach
    void provisionTwoTenantsWithAPayrollTrailForAcme() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        TenantContext.runWith(acme.tenant().getId(), () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            Employee employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            EmploymentContract contract = contracts.hire(employee.getId(), company.getId(), position.getId(),
                    null, null, ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("50000.00"), "DOP");

            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 1, 1));
            period.setEndDate(LocalDate.of(2025, 1, 31));
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);

            PayrollRun payrollRun = new PayrollRun();
            payrollRun.setPayrollPeriod(period);
            payrollRun.setCompanyId(company.getId());
            acmeRun = payrollRunRepository.save(payrollRun);

            Payslip payslip = new Payslip();
            payslip.setPayrollRun(acmeRun);
            payslip.setEmployeeId(employee.getId());
            payslip.setContractId(contract.getId());
            payslip.setGrossPay(new BigDecimal("50000.00"));
            payslip.setNetPay(new BigDecimal("45000.00"));
            payslip.setCurrency("DOP");
            acmePayslip = payslipRepository.save(payslip);

            PayslipLine line = new PayslipLine();
            line.setPayslip(acmePayslip);
            line.setDescription("Salario base");
            line.setType(PayrollConceptType.EARNING);
            line.setAmount(new BigDecimal("50000.00"));
            line.setLineOrder(0);
            payslipLineRepository.save(line);

            Accrual accrual = new Accrual();
            accrual.setPayrollRun(acmeRun);
            accrual.setEmployeeId(employee.getId());
            accrual.setConceptCode("SFS_PATRONAL");
            accrual.setType(PayrollConceptType.EMPLOYER_CONTRIBUTION);
            accrual.setAmount(new BigDecimal("3545.00"));
            accrualRepository.save(accrual);
        });
    }

    @Test
    @DisplayName("una corrida de nomina creada en un tenant no se ve consultando desde otro")
    void payrollRunsDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(), () -> payrollRunRepository.findById(acmeRun.getId())))
                .as("bajo Acme se resuelve la corrida recien creada")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(), () -> payrollRunRepository.findById(acmeRun.getId())))
                .as("el mismo id consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un volante y sus lineas no se ven consultando desde otro tenant")
    void payslipsAndTheirLinesDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(acmePayslip.getId())))
                .as("bajo Acme se ve la linea del volante")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(acmePayslip.getId())))
                .as("el mismo payslip_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un asiento de aportes patronales no se ve consultando desde otro tenant")
    void accrualsDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(), () -> accrualRepository.findByPayrollRunId(acmeRun.getId())))
                .as("bajo Acme se ve el aporte patronal registrado")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(), () -> accrualRepository.findByPayrollRunId(acmeRun.getId())))
                .as("el mismo payroll_run_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
