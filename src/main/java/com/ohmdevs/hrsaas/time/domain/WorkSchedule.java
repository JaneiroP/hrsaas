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

import java.math.BigDecimal;

/**
 * Plantilla de horario nombrada a nivel de empresa (p. ej. "Horario
 * Administrativo", "Horario Operativo"). A quién aplica y desde cuándo lo
 * dice {@link EmployeeWorkSchedule}, no esta entidad.
 */
@Entity
@Table(name = "work_schedules")
@Getter
@Setter
public class WorkSchedule extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "monday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal mondayHours = BigDecimal.ZERO;

    @Column(name = "tuesday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal tuesdayHours = BigDecimal.ZERO;

    @Column(name = "wednesday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal wednesdayHours = BigDecimal.ZERO;

    @Column(name = "thursday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal thursdayHours = BigDecimal.ZERO;

    @Column(name = "friday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal fridayHours = BigDecimal.ZERO;

    @Column(name = "saturday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal saturdayHours = BigDecimal.ZERO;

    @Column(name = "sunday_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal sundayHours = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
