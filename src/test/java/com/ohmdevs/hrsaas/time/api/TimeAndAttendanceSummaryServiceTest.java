package com.ohmdevs.hrsaas.time.api;

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
import com.ohmdevs.hrsaas.time.domain.HolidayCalendar;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import com.ohmdevs.hrsaas.time.domain.TimeEntrySource;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import com.ohmdevs.hrsaas.time.domain.WorkSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El test central de la Fase 2: reconstruir para un empleado y un período
 * los tres números que {@code payroll} (Fase 3) necesita — la definición
 * exacta de "Terminado cuando" del blueprint.
 *
 * <p>Período de prueba: lunes 2025-01-06 a viernes 2025-01-10 (semana
 * laboral completa), con un feriado el miércoles y un permiso sin sueldo el
 * jueves — deja solo lunes, martes y viernes como días efectivos.
 */
class TimeAndAttendanceSummaryServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private TimeTrackingService timeTracking;
    @Autowired
    private LeaveManagementService leaveManagement;
    @Autowired
    private TimeAndAttendanceSummaryService summaryService;

    private Long tenantId;
    private Long companyId;
    private Employee employee;

    @BeforeEach
    void provisionCompanyScheduleAndCalendar() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        TenantContext.runWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            companyId = company.getId();

            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");

            BigDecimal eightHours = new BigDecimal("8.00");
            BigDecimal zero = BigDecimal.ZERO;
            WorkSchedule schedule = timeTracking.createWorkSchedule(companyId, "Administrativo",
                    eightHours, eightHours, eightHours, eightHours, eightHours, zero, zero);
            timeTracking.assignWorkSchedule(employee.getId(), schedule, LocalDate.of(2025, 1, 1));

            HolidayCalendar calendar = timeTracking.createHolidayCalendar(companyId, null, "Nacional");
            timeTracking.addHoliday(calendar, LocalDate.of(2025, 1, 8), "Feriado de prueba");

            LeaveType unpaidLeave = leaveManagement.createLeaveType(
                    "SIN_SUELDO", "Permiso sin sueldo", false, true, null, null);
            var request = leaveManagement.submit(employee.getId(), unpaidLeave.getId(),
                    LocalDate.of(2025, 1, 9), LocalDate.of(2025, 1, 9), BigDecimal.ONE, "Prueba");
            leaveManagement.approve(request.getId(), 1L);

            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_IN,
                    Instant.parse("2025-01-06T08:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");
            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_OUT,
                    Instant.parse("2025-01-06T16:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");

            // Martes con dos horas extra.
            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_IN,
                    Instant.parse("2025-01-07T08:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");
            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_OUT,
                    Instant.parse("2025-01-07T18:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");

            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_IN,
                    Instant.parse("2025-01-10T08:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");
            timeTracking.recordTimeEntry(employee.getId(), TimeEntryType.CLOCK_OUT,
                    Instant.parse("2025-01-10T16:00:00Z"), TimeEntrySource.BIOMETRIC, "reloj-01");
        });
    }

    @Test
    @DisplayName("dias efectivos = laborables - feriado - permiso sin sueldo; horas extra del martes; un dia de ausencia sin remuneracion")
    void summarizesEffectiveDaysOvertimeAndUnpaidLeave() {
        AttendanceSummary summary = TenantContext.callWith(tenantId, () ->
                summaryService.summarize(employee.getId(), companyId,
                        LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));

        assertThat(summary.unpaidLeaveDays())
                .as("jueves 2025-01-09, permiso sin sueldo aprobado")
                .isEqualByComparingTo("1.00");
        assertThat(summary.effectiveDays())
                .as("lunes, martes y viernes: 5 laborables - miercoles feriado - jueves sin sueldo")
                .isEqualByComparingTo("3.00");
        assertThat(summary.overtimeHours())
                .as("martes trabajo 10h contra 8h programadas")
                .isEqualByComparingTo("2.00");
    }
}
