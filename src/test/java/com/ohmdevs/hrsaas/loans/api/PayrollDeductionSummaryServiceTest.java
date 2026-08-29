package com.ohmdevs.hrsaas.loans.api;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.loans.application.GarnishmentService;
import com.ohmdevs.hrsaas.loans.application.LoanManagementService;
import com.ohmdevs.hrsaas.loans.application.SalaryAdvanceService;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
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
 * El test central de "cuotas a descontar" de la Fase 2: para un empleado y
 * un período, {@code loans} debe exponer sus tres fuentes de descuento
 * pendientes.
 */
class PayrollDeductionSummaryServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private LoanManagementService loanManagement;
    @Autowired
    private SalaryAdvanceService salaryAdvanceService;
    @Autowired
    private GarnishmentService garnishmentService;
    @Autowired
    private PayrollDeductionSummaryService summaryService;

    private Long tenantId;
    private Employee employee;

    @BeforeEach
    void provisionTenantAndEmployee() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        TenantContext.runWith(tenantId, () -> {
            orgStructure.createCompany("Acme RD", "Acme", "101234567");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
        });
    }

    @Test
    @DisplayName("resume cuota de prestamo del periodo, avance aprobado del periodo, y embargo activo")
    void summarizesAllThreeDeductionSources() {
        LocalDate periodStart = LocalDate.of(2025, 3, 1);
        LocalDate periodEnd = LocalDate.of(2025, 3, 31);

        TenantContext.runWith(tenantId, () -> {
            // Prestamo de 3 cuotas mensuales desde 2025-01-01: vencen 02-01,
            // 03-01, 04-01 -- la segunda cae dentro del periodo de marzo.
            loanManagement.createLoan(employee.getId(), new BigDecimal("300.00"), 3,
                    LocalDate.of(2025, 1, 1), "Prueba");

            var advance = salaryAdvanceService.request(employee.getId(), new BigDecimal("1500.00"),
                    LocalDate.of(2025, 3, 5));
            salaryAdvanceService.approve(advance.getId(), LocalDate.of(2025, 3, 15));

            garnishmentService.create(employee.getId(), "EXP-002", "Acreedor de prueba",
                    null, new BigDecimal("10.00"), 1, LocalDate.of(2025, 1, 1));
        });

        PayrollDeductionSummary summary = TenantContext.callWith(tenantId,
                () -> summaryService.summarize(employee.getId(), periodStart, periodEnd));

        assertThat(summary.loanInstallments()).hasSize(1);
        assertThat(summary.loanInstallments().get(0).amount()).isEqualByComparingTo("100.00");

        assertThat(summary.salaryAdvances()).hasSize(1);
        assertThat(summary.salaryAdvances().get(0).amount()).isEqualByComparingTo("1500.00");

        assertThat(summary.garnishments()).hasSize(1);
        assertThat(summary.garnishments().get(0).percentage()).isEqualByComparingTo("10.00");
        assertThat(summary.garnishments().get(0).priority()).isEqualTo(1);
    }
}
