package com.ohmdevs.hrsaas.time.application;

import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.domain.EmployeeWorkSchedule;
import com.ohmdevs.hrsaas.time.domain.Holiday;
import com.ohmdevs.hrsaas.time.domain.HolidayCalendar;
import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import com.ohmdevs.hrsaas.time.domain.TimeEntrySource;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import com.ohmdevs.hrsaas.time.domain.WorkSchedule;
import com.ohmdevs.hrsaas.time.infrastructure.EmployeeWorkScheduleRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayCalendarRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayRepository;
import com.ohmdevs.hrsaas.time.infrastructure.TimeEntryRepository;
import com.ohmdevs.hrsaas.time.infrastructure.WorkScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Calendarios de feriados, horarios de trabajo y marcajes crudos. */
@Service
public class TimeTrackingService {

    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayRepository holidayRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final EmployeeWorkScheduleRepository employeeWorkScheduleRepository;
    private final TimeEntryRepository timeEntryRepository;

    public TimeTrackingService(HolidayCalendarRepository holidayCalendarRepository,
                                HolidayRepository holidayRepository,
                                WorkScheduleRepository workScheduleRepository,
                                EmployeeWorkScheduleRepository employeeWorkScheduleRepository,
                                TimeEntryRepository timeEntryRepository) {
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayRepository = holidayRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.employeeWorkScheduleRepository = employeeWorkScheduleRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    @Transactional
    public HolidayCalendar createHolidayCalendar(Long companyId, Long locationId, String name) {
        TenantContext.require();
        HolidayCalendar calendar = new HolidayCalendar();
        calendar.setCompanyId(companyId);
        calendar.setLocationId(locationId);
        calendar.setName(name);
        return holidayCalendarRepository.save(calendar);
    }

    @Transactional
    public Holiday addHoliday(HolidayCalendar calendar, LocalDate date, String name) {
        TenantContext.require();
        Holiday holiday = new Holiday();
        holiday.setHolidayCalendar(calendar);
        holiday.setDate(date);
        holiday.setName(name);
        return holidayRepository.save(holiday);
    }

    /**
     * Recibe las siete horas ya armadas en vez de crear con ceros y esperar
     * que quien llama las ajuste después: un {@link WorkSchedule} devuelto
     * por {@code save()} sale del método ya {@code DETACHED} (la transacción
     * de este método cierra al retornar) — mutar sus campos después no
     * dispara ningún {@code UPDATE}, Hibernate ya dejó de rastrearlo. Un
     * error real de esta forma se encontró escribiendo el test de
     * {@code TimeAndAttendanceSummaryService}.
     */
    @Transactional
    public WorkSchedule createWorkSchedule(Long companyId, String name,
                                            BigDecimal mondayHours, BigDecimal tuesdayHours,
                                            BigDecimal wednesdayHours, BigDecimal thursdayHours,
                                            BigDecimal fridayHours, BigDecimal saturdayHours,
                                            BigDecimal sundayHours) {
        TenantContext.require();
        WorkSchedule schedule = new WorkSchedule();
        schedule.setCompanyId(companyId);
        schedule.setName(name);
        schedule.setMondayHours(mondayHours);
        schedule.setTuesdayHours(tuesdayHours);
        schedule.setWednesdayHours(wednesdayHours);
        schedule.setThursdayHours(thursdayHours);
        schedule.setFridayHours(fridayHours);
        schedule.setSaturdayHours(saturdayHours);
        schedule.setSundayHours(sundayHours);
        return workScheduleRepository.save(schedule);
    }

    /** Cierra la asignación vigente (si hay una) y abre la nueva — nunca se edita en el sitio. */
    @Transactional
    public EmployeeWorkSchedule assignWorkSchedule(Long employeeId, WorkSchedule workSchedule, LocalDate effectiveFrom) {
        TenantContext.require();
        employeeWorkScheduleRepository.findByEmployeeIdAndEffectiveToIsNull(employeeId).ifPresent(current -> {
            current.setEffectiveTo(effectiveFrom.minusDays(1));
            employeeWorkScheduleRepository.saveAndFlush(current);
        });

        EmployeeWorkSchedule assignment = new EmployeeWorkSchedule();
        assignment.setEmployeeId(employeeId);
        assignment.setWorkSchedule(workSchedule);
        assignment.setEffectiveFrom(effectiveFrom);
        return employeeWorkScheduleRepository.save(assignment);
    }

    @Transactional
    public TimeEntry recordTimeEntry(Long employeeId, TimeEntryType type, Instant occurredAt,
                                      TimeEntrySource source, String deviceId) {
        TenantContext.require();
        TimeEntry entry = new TimeEntry();
        entry.setEmployeeId(employeeId);
        entry.setType(type);
        entry.setOccurredAt(occurredAt);
        entry.setSource(source);
        entry.setDeviceId(deviceId);
        return timeEntryRepository.save(entry);
    }
}
