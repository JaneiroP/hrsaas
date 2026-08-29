package com.ohmdevs.hrsaas.time.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Calendario de feriados con nombre, para poder tener uno nacional y otros
 * específicos de sucursal ("calendarios por sucursal" del blueprint) sin
 * tocar el esquema de {@code org} — {@link #locationId} es nulo para un
 * calendario que aplica a toda la empresa.
 */
@Entity
@Table(name = "holiday_calendars")
@Getter
@Setter
public class HolidayCalendar extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(nullable = false, length = 160)
    private String name;
}
