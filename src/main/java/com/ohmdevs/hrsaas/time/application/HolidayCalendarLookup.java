package com.ohmdevs.hrsaas.time.application;

import com.ohmdevs.hrsaas.time.domain.Holiday;
import com.ohmdevs.hrsaas.time.domain.HolidayCalendar;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayCalendarRepository;
import com.ohmdevs.hrsaas.time.infrastructure.HolidayRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feriados de toda la empresa (calendario con {@code locationId} nulo) dentro
 * de un rango de fechas.
 *
 * <p>Extraído de {@code TimeAndAttendanceSummaryService} para que
 * {@code LeaveSelfServiceQueryService} (portal, Fase 5 adelantada) no
 * duplique la misma consulta al calcular días hábiles de una solicitud de
 * licencia — ambos necesitan exactamente "qué días son feriado para esta
 * empresa entre estas dos fechas", nada específico de nómina ni de licencias.
 * Mismas simplificaciones deliberadas que documentaba el método original: no
 * distingue calendario por sucursal aunque el modelo lo permite.
 */
public final class HolidayCalendarLookup {

    private HolidayCalendarLookup() {
    }

    public static Set<LocalDate> companyWideHolidays(HolidayCalendarRepository holidayCalendarRepository,
                                               HolidayRepository holidayRepository,
                                               Long companyId, LocalDate periodStart, LocalDate periodEnd) {
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
}
