package com.ohmdevs.hrsaas.time.api;

import com.ohmdevs.hrsaas.time.domain.EmployeeWorkSchedule;
import com.ohmdevs.hrsaas.time.domain.Holiday;
import com.ohmdevs.hrsaas.time.domain.HolidayCalendar;
import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import com.ohmdevs.hrsaas.time.domain.WorkSchedule;
import com.ohmdevs.hrsaas.time.infrastructure.EmployeeWorkScheduleRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayCalendarRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayRepository;
import com.ohmdevs.hrsaas.time.infrastructure.LeaveRequestRepository;
import com.ohmdevs.hrsaas.time.infrastructure.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Único contenido público de {@code time}: lo que {@code payroll} (Fase 3)
 * necesita para un empleado y un período.
 *
 * <p><strong>Simplificaciones deliberadas de esta fase</strong> (revisar
 * cuando {@code payroll} llegue y exija precisión de nómina real):
 * <ul>
 *   <li>El horario vigente se toma el que esté abierto <em>hoy</em>
 *   ({@code effectiveTo IS NULL}), no el que estuviera vigente en cada fecha
 *   del período — un cambio de horario a mitad de período no se refleja.</li>
 *   <li>Las horas trabajadas por día se calculan como
 *   {@code CLOCK_OUT - CLOCK_IN}, sin restar {@code BREAK_START}/
 *   {@code BREAK_END} — un almuerzo marcado cuenta como tiempo trabajado.</li>
 *   <li>El calendario de feriados usado es el de la empresa completa
 *   ({@code locationId} nulo); no distingue por sucursal aunque el modelo lo
 *   permite.</li>
 * </ul>
 */
@Service
public class TimeAndAttendanceSummaryService {

    private final EmployeeWorkScheduleRepository employeeWorkScheduleRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimeEntryRepository timeEntryRepository;

    public TimeAndAttendanceSummaryService(EmployeeWorkScheduleRepository employeeWorkScheduleRepository,
                                            HolidayCalendarRepository holidayCalendarRepository,
                                            HolidayRepository holidayRepository,
                                            LeaveRequestRepository leaveRequestRepository,
                                            TimeEntryRepository timeEntryRepository) {
        this.employeeWorkScheduleRepository = employeeWorkScheduleRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    @Transactional
    public AttendanceSummary summarize(Long employeeId, Long companyId, LocalDate periodStart, LocalDate periodEnd) {
        WorkSchedule schedule = employeeWorkScheduleRepository.findByEmployeeIdAndEffectiveToIsNull(employeeId)
                .map(EmployeeWorkSchedule::getWorkSchedule)
                .orElse(null);

        Set<LocalDate> holidays = companyWideHolidays(companyId, periodStart, periodEnd);

        BigDecimal scheduledDays = BigDecimal.ZERO;
        if (schedule != null) {
            for (LocalDate day = periodStart; !day.isAfter(periodEnd); day = day.plusDays(1)) {
                if (!holidays.contains(day) && scheduledHoursFor(schedule, day).signum() > 0) {
                    scheduledDays = scheduledDays.add(BigDecimal.ONE);
                }
            }
        }

        BigDecimal unpaidLeaveDays = leaveRequestRepository
                .findByEmployeeIdAndStatus(employeeId, LeaveRequestStatus.APPROVED).stream()
                .filter(request -> !request.getLeaveType().isPaid())
                .filter(request -> overlaps(request, periodStart, periodEnd))
                .map(LeaveRequest::getDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal effectiveDays = scheduledDays.subtract(unpaidLeaveDays).max(BigDecimal.ZERO);
        BigDecimal overtimeHours = computeOvertimeHours(employeeId, schedule, periodStart, periodEnd);

        return new AttendanceSummary(effectiveDays, overtimeHours, unpaidLeaveDays);
    }

    private Set<LocalDate> companyWideHolidays(Long companyId, LocalDate periodStart, LocalDate periodEnd) {
        return holidayCalendarRepository.findByCompanyId(companyId).stream()
                .filter(calendar -> calendar.getLocationId() == null)
                .findFirst()
                .map(HolidayCalendar::getId)
                .map(calendarId -> holidayRepository.findByHolidayCalendarIdAndDateBetween(calendarId, periodStart, periodEnd))
                .orElseGet(List::of)
                .stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
    }

    private boolean overlaps(LeaveRequest request, LocalDate periodStart, LocalDate periodEnd) {
        return !request.getStartDate().isAfter(periodEnd) && !request.getEndDate().isBefore(periodStart);
    }

    private BigDecimal computeOvertimeHours(Long employeeId, WorkSchedule schedule,
                                             LocalDate periodStart, LocalDate periodEnd) {
        if (schedule == null) {
            return BigDecimal.ZERO;
        }
        Instant from = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TimeEntry> entries = timeEntryRepository
                .findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(employeeId, from, to);

        Map<LocalDate, BigDecimal> workedHoursByDay = new HashMap<>();
        Instant pendingClockIn = null;
        for (TimeEntry entry : entries) {
            if (entry.getType() == TimeEntryType.CLOCK_IN) {
                pendingClockIn = entry.getOccurredAt();
            } else if (entry.getType() == TimeEntryType.CLOCK_OUT && pendingClockIn != null) {
                Duration worked = Duration.between(pendingClockIn, entry.getOccurredAt());
                LocalDate day = LocalDateTime.ofInstant(pendingClockIn, ZoneOffset.UTC).toLocalDate();
                BigDecimal hours = BigDecimal.valueOf(worked.toMinutes())
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                workedHoursByDay.merge(day, hours, BigDecimal::add);
                pendingClockIn = null;
            }
        }

        BigDecimal overtime = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> worked : workedHoursByDay.entrySet()) {
            BigDecimal scheduledHours = scheduledHoursFor(schedule, worked.getKey());
            if (worked.getValue().compareTo(scheduledHours) > 0) {
                overtime = overtime.add(worked.getValue().subtract(scheduledHours));
            }
        }
        return overtime;
    }

    private static BigDecimal scheduledHoursFor(WorkSchedule schedule, LocalDate day) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        return switch (dayOfWeek) {
            case MONDAY -> schedule.getMondayHours();
            case TUESDAY -> schedule.getTuesdayHours();
            case WEDNESDAY -> schedule.getWednesdayHours();
            case THURSDAY -> schedule.getThursdayHours();
            case FRIDAY -> schedule.getFridayHours();
            case SATURDAY -> schedule.getSaturdayHours();
            case SUNDAY -> schedule.getSundayHours();
        };
    }
}
