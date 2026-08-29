package com.ohmdevs.hrsaas.loans;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.loans.application.GarnishmentService;
import com.ohmdevs.hrsaas.loans.application.LoanManagementService;
import com.ohmdevs.hrsaas.loans.domain.GarnishmentOrder;
import com.ohmdevs.hrsaas.loans.domain.LoanAgreement;
import com.ohmdevs.hrsaas.loans.infrastructure.GarnishmentOrderRepository;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanAgreementRepository;
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

/** Aislamiento cruzado del módulo {@code loans}, mismo patrón que el resto de la suite. */
class LoansIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private LoanManagementService loanManagement;
    @Autowired
    private GarnishmentService garnishmentService;
    @Autowired
    private LoanAgreementRepository loanAgreementRepository;
    @Autowired
    private GarnishmentOrderRepository garnishmentOrderRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private Employee acmeEmployee;

    @BeforeEach
    void provisionTwoTenants() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        acmeEmployee = TenantContext.callWith(acme.tenant().getId(), () -> {
            orgStructure.createCompany("Acme RD", "Acme", "101234567");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            return employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
        });
    }

    @Test
    @DisplayName("un prestamo no se ve consultando desde otro tenant")
    void loanAgreementsDoNotLeakBetweenTenants() {
        LoanAgreement created = TenantContext.callWith(acme.tenant().getId(), () ->
                loanManagement.createLoan(acmeEmployee.getId(), new BigDecimal("500.00"), 5,
                        LocalDate.of(2025, 1, 1), "Prueba"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> loanAgreementRepository.findByEmployeeId(acmeEmployee.getId())))
                .extracting(LoanAgreement::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> loanAgreementRepository.findByEmployeeId(acmeEmployee.getId())))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un embargo no se ve consultando desde otro tenant")
    void garnishmentOrdersDoNotLeakBetweenTenants() {
        GarnishmentOrder created = TenantContext.callWith(acme.tenant().getId(), () ->
                garnishmentService.create(acmeEmployee.getId(), "EXP-001", "Acreedor de prueba",
                        new BigDecimal("1000.00"), null, 1, LocalDate.of(2025, 1, 1)));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> garnishmentOrderRepository.findByEmployeeId(acmeEmployee.getId())))
                .extracting(GarnishmentOrder::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> garnishmentOrderRepository.findByEmployeeId(acmeEmployee.getId())))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
