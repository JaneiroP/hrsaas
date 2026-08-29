package com.ohmdevs.hrsaas.time.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import com.ohmdevs.hrsaas.time.domain.LeaveBalance;
import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveManagementServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private LeaveManagementService leaveManagement;
    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    private Long tenantId;
    private Employee employee;
    private LeaveType vacation;

    @BeforeEach
    void provisionTenantAndLeaveType() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        TenantContext.runWith(tenantId, () -> {
            orgStructure.createCompany("Acme RD", "Acme", "101234567");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            vacation = leaveManagement.createLeaveType("VAC", "Vacaciones", true, true,
                    new BigDecimal("14.00"), 5);
        });
    }

    @Test
    @DisplayName("aprobar una solicitud descuenta el saldo; cancelarla despues lo repone")
    void approveDeductsBalanceAndCancelRestoresIt() {
        TenantContext.runWith(tenantId, () -> leaveManagement.accrue(employee.getId(), vacation.getId(),
                new BigDecimal("10.00")));

        LeaveRequest request = TenantContext.callWith(tenantId, () -> leaveManagement.submit(
                employee.getId(), vacation.getId(), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5),
                new BigDecimal("5.00"), "Vacaciones de verano"));

        TenantContext.runWith(tenantId, () -> leaveManagement.approve(request.getId(), 1L));
        LeaveBalance afterApproval = TenantContext.callWith(tenantId, () ->
                leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(employee.getId(), vacation.getId())
                        .orElseThrow());
        assertThat(afterApproval.getAvailableDays()).isEqualByComparingTo("5.00");
        assertThat(afterApproval.getUsedDays()).isEqualByComparingTo("5.00");

        TenantContext.runWith(tenantId, () -> leaveManagement.cancel(request.getId()));
        LeaveBalance afterCancel = TenantContext.callWith(tenantId, () ->
                leaveBalanceRepository.findByEmployeeIdAndLeaveTypeId(employee.getId(), vacation.getId())
                        .orElseThrow());
        assertThat(afterCancel.getAvailableDays()).isEqualByComparingTo("10.00");
        assertThat(afterCancel.getUsedDays()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("una solicitud que excede el saldo disponible se rechaza al enviarla")
    void submitRejectsWhenBalanceInsufficient() {
        TenantContext.runWith(tenantId, () -> leaveManagement.accrue(employee.getId(), vacation.getId(),
                new BigDecimal("2.00")));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId, () -> leaveManagement.submit(
                employee.getId(), vacation.getId(), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5),
                new BigDecimal("5.00"), "Mas de lo que tiene")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un tipo de licencia sin devengo no exige saldo")
    void unpaidLeaveTypeDoesNotRequireBalance() {
        LeaveType unpaidLeave = TenantContext.callWith(tenantId, () ->
                leaveManagement.createLeaveType("SIN_SUELDO", "Permiso sin sueldo", false, true, null, null));

        LeaveRequest request = TenantContext.callWith(tenantId, () -> leaveManagement.submit(
                employee.getId(), unpaidLeave.getId(), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2),
                new BigDecimal("2.00"), "Sin saldo que devengar"));

        assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
    }

    @Test
    @DisplayName("rechazar una solicitud ya aprobada lanza IllegalStateTransitionException")
    void rejectingAnApprovedRequestIsIllegal() {
        TenantContext.runWith(tenantId, () -> leaveManagement.accrue(employee.getId(), vacation.getId(),
                new BigDecimal("5.00")));
        LeaveRequest request = TenantContext.callWith(tenantId, () -> leaveManagement.submit(
                employee.getId(), vacation.getId(), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 1),
                BigDecimal.ONE, null));
        TenantContext.runWith(tenantId, () -> leaveManagement.approve(request.getId(), 1L));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId, () -> leaveManagement.reject(request.getId(), 1L)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
