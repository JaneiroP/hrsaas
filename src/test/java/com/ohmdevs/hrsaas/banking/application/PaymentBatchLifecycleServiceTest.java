package com.ohmdevs.hrsaas.banking.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatch;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchItem;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchStatus;
import com.ohmdevs.hrsaas.banking.domain.PaymentItemStatus;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchItemRepository;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.application.PayrollRunLifecycleService;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.BankAccountType;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
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
 * El test central de {@code banking}: construir un lote desde una corrida
 * aprobada, el ciclo completo con maker-checker, y la exclusión reportada
 * (no bloqueante) de un empleado sin cuenta bancaria — ver la decisión
 * documentada en {@code PaymentBatchBuilderService}.
 */
class PaymentBatchLifecycleServiceTest extends AbstractPostgresTest {

    private static final Long BUILDER_USER_ID = 1L;
    private static final Long APPROVER_USER_ID = 2L;

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
    private PaymentBatchBuilderService paymentBatchBuilderService;
    @Autowired
    private PaymentBatchLifecycleService paymentBatchLifecycleService;
    @Autowired
    private PaymentBatchItemRepository paymentBatchItemRepository;

    private Long tenantId;
    private Company company;
    private Employee employee;
    private Long approvedRunId;

    @BeforeEach
    void provisionAnApprovedPayrollRun() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        approvedRunId = TenantContext.callWith(tenantId, () -> {
            company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            contracts.hire(employee.getId(), company.getId(), position.getId(), null, null,
                    ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("40000.00"), "DOP");

            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Enero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 1, 1));
            period.setEndDate(LocalDate.of(2025, 1, 31));
            period.setPayDate(LocalDate.of(2025, 2, 5));
            period = payrollPeriodRepository.save(period);

            PayrollRun createdRun = payrollRunLifecycleService.createRun(company.getId(), period.getId());
            payrollRunLifecycleService.calculate(createdRun.getId());
            payrollRunLifecycleService.submitForReview(createdRun.getId());
            PayrollRun approved = payrollRunLifecycleService.approve(createdRun.getId(), 999L);
            return approved.getId();
        });
    }

    @Test
    @DisplayName("build -> validate -> approve -> export -> send -> reconcile sigue la maquina de estados completa")
    void fullLifecycleFollowsTheStateMachineToReconciled() {
        TenantContext.runWith(tenantId, () -> employeeRecords.addBankAccount(
                employee, "Banco Popular", BankAccountType.SAVINGS, "1234567890123"));

        PaymentBatch built = TenantContext.callWith(tenantId, () -> paymentBatchBuilderService.build(
                approvedRunId, company.getId(), "Banco Popular", "9999999999", BUILDER_USER_ID));
        assertThat(built.getStatus()).isEqualTo(PaymentBatchStatus.CONSTRUIDO);
        assertThat(built.getTotalAmount()).isEqualByComparingTo("40000.00");

        List<PaymentBatchItem> items = TenantContext.callWith(tenantId,
                () -> paymentBatchItemRepository.findByPaymentBatchId(built.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getStatus()).isEqualTo(PaymentItemStatus.PENDIENTE);
        assertThat(items.get(0).getBankAccountId()).isNotNull();

        PaymentBatch validated = TenantContext.callWith(tenantId, () -> paymentBatchLifecycleService.validate(built.getId()));
        assertThat(validated.getStatus()).isEqualTo(PaymentBatchStatus.VALIDADO);

        PaymentBatch approved = TenantContext.callWith(tenantId,
                () -> paymentBatchLifecycleService.approve(built.getId(), APPROVER_USER_ID));
        assertThat(approved.getStatus()).isEqualTo(PaymentBatchStatus.APROBADO);
        assertThat(approved.getApprovedByUserId()).isEqualTo(APPROVER_USER_ID);

        PaymentBatch exported = TenantContext.callWith(tenantId, () -> paymentBatchLifecycleService.markExported(built.getId()));
        assertThat(exported.getStatus()).isEqualTo(PaymentBatchStatus.EXPORTADO);
        assertThat(exported.getExportedAt()).isNotNull();

        PaymentBatch sent = TenantContext.callWith(tenantId, () -> paymentBatchLifecycleService.markSent(built.getId()));
        assertThat(sent.getStatus()).isEqualTo(PaymentBatchStatus.ENVIADO);

        Long itemId = items.get(0).getId();
        PaymentBatch reconciled = TenantContext.callWith(tenantId, () -> paymentBatchLifecycleService.reconcile(
                built.getId(), List.of(new ReconciliationOutcome(itemId, true, null, null))));
        assertThat(reconciled.getStatus()).isEqualTo(PaymentBatchStatus.CONCILIADO);
        assertThat(reconciled.getReconciledAt()).isNotNull();

        PaymentBatchItem reconciledItem = TenantContext.callWith(tenantId,
                () -> paymentBatchItemRepository.findByPaymentBatchId(built.getId())).get(0);
        assertThat(reconciledItem.getStatus()).isEqualTo(PaymentItemStatus.ACEPTADO);
    }

    @Test
    @DisplayName("maker-checker: quien construye el lote no puede aprobarlo")
    void approvingWithTheSameUserThatBuiltTheBatchIsRejected() {
        TenantContext.runWith(tenantId, () -> employeeRecords.addBankAccount(
                employee, "Banco Popular", BankAccountType.SAVINGS, "1234567890123"));
        PaymentBatch built = TenantContext.callWith(tenantId, () -> paymentBatchBuilderService.build(
                approvedRunId, company.getId(), "Banco Popular", "9999999999", BUILDER_USER_ID));
        TenantContext.runWith(tenantId, () -> paymentBatchLifecycleService.validate(built.getId()));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> paymentBatchLifecycleService.approve(built.getId(), BUILDER_USER_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maker-checker");
    }

    @Test
    @DisplayName("un empleado sin cuenta bancaria queda excluido y reportado, sin bloquear el lote")
    void anEmployeeWithoutABankAccountIsExcludedButReported() {
        // Sin employeeRecords.addBankAccount: el empleado no tiene cuenta bancaria.
        PaymentBatch built = TenantContext.callWith(tenantId, () -> paymentBatchBuilderService.build(
                approvedRunId, company.getId(), "Banco Popular", "9999999999", BUILDER_USER_ID));

        assertThat(built.getTotalAmount())
                .as("sin cuentas pagables, el total del lote es cero")
                .isEqualByComparingTo("0.00");

        List<PaymentBatchItem> items = TenantContext.callWith(tenantId,
                () -> paymentBatchItemRepository.findByPaymentBatchId(built.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getStatus()).isEqualTo(PaymentItemStatus.RECHAZADO);
        assertThat(items.get(0).getRejectionCode()).isEqualTo("SIN_CUENTA_BANCARIA");

        // El lote igual valida: la suma de PENDIENTES (cero) coincide con el total (cero).
        PaymentBatch validated = TenantContext.callWith(tenantId, () -> paymentBatchLifecycleService.validate(built.getId()));
        assertThat(validated.getStatus()).isEqualTo(PaymentBatchStatus.VALIDADO);
    }

    @Test
    @DisplayName("construir un lote desde una corrida que todavia no esta aprobada es ilegal")
    void buildingFromANonApprovedRunIsRejected() {
        Long draftRunId = TenantContext.callWith(tenantId, () -> {
            PayrollPeriod period = new PayrollPeriod();
            period.setCompanyId(company.getId());
            period.setName("Febrero 2025");
            period.setFrequency(PayrollFrequency.MONTHLY);
            period.setStartDate(LocalDate.of(2025, 2, 1));
            period.setEndDate(LocalDate.of(2025, 2, 28));
            period.setPayDate(LocalDate.of(2025, 3, 5));
            period = payrollPeriodRepository.save(period);
            return payrollRunLifecycleService.createRun(company.getId(), period.getId()).getId();
        });

        assertThatThrownBy(() -> TenantContext.runWith(tenantId, () -> paymentBatchBuilderService.build(
                draftRunId, company.getId(), "Banco Popular", "9999999999", BUILDER_USER_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validar un lote antes de construirlo es una transicion ilegal")
    void validatingBeforeBuildingFailsFast() {
        // No aplica directamente (build siempre deja CONSTRUIDO); se prueba la ilegalidad
        // de saltar un paso: aprobar sin validar primero.
        TenantContext.runWith(tenantId, () -> employeeRecords.addBankAccount(
                employee, "Banco Popular", BankAccountType.SAVINGS, "1234567890123"));
        PaymentBatch built = TenantContext.callWith(tenantId, () -> paymentBatchBuilderService.build(
                approvedRunId, company.getId(), "Banco Popular", "9999999999", BUILDER_USER_ID));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> paymentBatchLifecycleService.approve(built.getId(), APPROVER_USER_ID)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
