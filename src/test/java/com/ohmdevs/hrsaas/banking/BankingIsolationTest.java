package com.ohmdevs.hrsaas.banking;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatch;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchItem;
import com.ohmdevs.hrsaas.banking.domain.PaymentItemStatus;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchItemRepository;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchRepository;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
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

/** Aislamiento cruzado de {@code banking} (AD-02) — mismo patrón que el resto de la suite. */
class BankingIsolationTest extends AbstractPostgresTest {

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
    private PaymentBatchRepository paymentBatchRepository;
    @Autowired
    private PaymentBatchItemRepository paymentBatchItemRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private PaymentBatch acmeBatch;

    @BeforeEach
    void provisionTwoTenantsWithAPaymentBatchForAcme() {
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
                    new BigDecimal("40000.00"), "DOP");

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
            payrollRun = payrollRunRepository.save(payrollRun);

            PaymentBatch batch = new PaymentBatch();
            batch.setPayrollRunId(payrollRun.getId());
            batch.setCompanyId(company.getId());
            batch.setBankName("Banco Popular");
            batch.setOriginAccountNumber("1234567890");
            batch.setTotalAmount(new BigDecimal("40000.00"));
            batch.setCurrency("DOP");
            batch.setBuiltByUserId(1L);
            acmeBatch = paymentBatchRepository.save(batch);

            PaymentBatchItem item = new PaymentBatchItem();
            item.setPaymentBatch(acmeBatch);
            item.setEmployeeId(employee.getId());
            item.setContractId(contract.getId());
            item.setAmount(new BigDecimal("40000.00"));
            item.setStatus(PaymentItemStatus.PENDIENTE);
            paymentBatchItemRepository.save(item);
        });
    }

    @Test
    @DisplayName("un lote de pago creado en un tenant no se ve consultando desde otro")
    void paymentBatchesDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(), () -> paymentBatchRepository.findById(acmeBatch.getId())))
                .as("bajo Acme se resuelve el lote recien creado")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(), () -> paymentBatchRepository.findById(acmeBatch.getId())))
                .as("el mismo id consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("los items de un lote no se ven consultando desde otro tenant")
    void paymentBatchItemsDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> paymentBatchItemRepository.findByPaymentBatchId(acmeBatch.getId())))
                .as("bajo Acme se ve el item del lote")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> paymentBatchItemRepository.findByPaymentBatchId(acmeBatch.getId())))
                .as("el mismo payment_batch_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
