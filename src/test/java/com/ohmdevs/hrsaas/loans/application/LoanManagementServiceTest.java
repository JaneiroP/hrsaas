package com.ohmdevs.hrsaas.loans.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.loans.domain.LoanAgreement;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallment;
import com.ohmdevs.hrsaas.loans.domain.LoanStatus;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanAgreementRepository;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanInstallmentRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoanManagementServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private LoanManagementService loanManagement;
    @Autowired
    private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired
    private LoanAgreementRepository loanAgreementRepository;

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
    @DisplayName("la tabla de amortizacion suma exacto al principal, incluso cuando no divide parejo")
    void amortizationScheduleSumsExactlyToPrincipal() {
        LoanAgreement loan = TenantContext.callWith(tenantId, () -> loanManagement.createLoan(
                employee.getId(), new BigDecimal("1000.00"), 3, LocalDate.of(2025, 1, 1), "Prueba de residuo"));

        List<LoanInstallment> installments = TenantContext.callWith(tenantId,
                () -> loanInstallmentRepository.findByLoanAgreementId(loan.getId()));

        assertThat(installments).hasSize(3);
        BigDecimal total = installments.stream().map(LoanInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("1000.00");
        // 1000 / 3 = 333.33 con DOWN; las dos primeras cuotas son 333.33, la
        // ultima absorbe el residuo: 1000.00 - 666.66 = 333.34.
        assertThat(installments.get(0).getAmount()).isEqualByComparingTo("333.33");
        assertThat(installments.get(1).getAmount()).isEqualByComparingTo("333.33");
        assertThat(installments.get(2).getAmount()).isEqualByComparingTo("333.34");
    }

    @Test
    @DisplayName("pagar la ultima cuota pendiente cierra el prestamo como PAID_OFF")
    void payingLastInstallmentClosesTheLoan() {
        LoanAgreement loan = TenantContext.callWith(tenantId, () -> loanManagement.createLoan(
                employee.getId(), new BigDecimal("200.00"), 2, LocalDate.of(2025, 1, 1), "Prueba"));
        List<LoanInstallment> installments = TenantContext.callWith(tenantId,
                () -> loanInstallmentRepository.findByLoanAgreementId(loan.getId()));

        TenantContext.runWith(tenantId, () -> loanManagement.payInstallment(installments.get(0).getId()));
        LoanAgreement stillActive = TenantContext.callWith(tenantId,
                () -> loanAgreementRepository.findById(loan.getId()).orElseThrow());
        assertThat(stillActive.getStatus()).isEqualTo(LoanStatus.ACTIVE);

        TenantContext.runWith(tenantId, () -> loanManagement.payInstallment(installments.get(1).getId()));
        LoanAgreement closed = TenantContext.callWith(tenantId,
                () -> loanAgreementRepository.findById(loan.getId()).orElseThrow());
        assertThat(closed.getStatus()).isEqualTo(LoanStatus.PAID_OFF);
    }
}
