package com.ohmdevs.hrsaas.time;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.application.LeaveManagementService;
import com.ohmdevs.hrsaas.time.application.TimeTrackingService;
import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import com.ohmdevs.hrsaas.time.domain.TimeEntrySource;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveRequestRepository;
import com.ohmdevs.hrsaas.time.infrastructure.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aislamiento cruzado del módulo {@code time}, mismo patrón que el resto de la suite. */
class TimeIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private LeaveManagementService leaveManagement;
    @Autowired
    private TimeTrackingService timeTracking;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private Employee acmeEmployee;

    @BeforeEach
    void provisionTwoTenants() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        acmeEmployee = TenantContext.callWith(acme.tenant().getId(), () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            return employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
        });
    }

    @Test
    @DisplayName("una solicitud de ausencia no se ve consultando desde otro tenant")
    void leaveRequestsDoNotLeakBetweenTenants() {
        LeaveRequest created = TenantContext.callWith(acme.tenant().getId(), () -> {
            LeaveType vacation = leaveManagement.createLeaveType("VAC", "Vacaciones", true, true,
                    new BigDecimal("14.00"), 5);
            leaveManagement.accrue(acmeEmployee.getId(), vacation.getId(), new BigDecimal("5.00"));
            return leaveManagement.submit(acmeEmployee.getId(), vacation.getId(),
                    LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), BigDecimal.ONE, "Prueba");
        });

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> leaveRequestRepository.findByEmployeeId(acmeEmployee.getId())))
                .as("bajo Acme se ve la solicitud recien creada")
                .extracting(LeaveRequest::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> leaveRequestRepository.findByEmployeeId(acmeEmployee.getId())))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un marcaje no se ve consultando desde otro tenant")
    void timeEntriesDoNotLeakBetweenTenants() {
        Instant occurredAt = Instant.parse("2025-01-02T13:00:00Z");
        TimeEntry created = TenantContext.callWith(acme.tenant().getId(), () ->
                timeTracking.recordTimeEntry(acmeEmployee.getId(), TimeEntryType.CLOCK_IN, occurredAt,
                        TimeEntrySource.BIOMETRIC, "reloj-01"));

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-03T00:00:00Z");

        assertThat(TenantContext.callWith(acme.tenant().getId(), () ->
                        timeEntryRepository.findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                                acmeEmployee.getId(), from, to)))
                .as("bajo Acme se ve el marcaje recien creado")
                .extracting(TimeEntry::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(), () ->
                        timeEntryRepository.findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                                acmeEmployee.getId(), from, to)))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
