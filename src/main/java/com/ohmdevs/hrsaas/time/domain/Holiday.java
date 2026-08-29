package com.ohmdevs.hrsaas.time.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Un día feriado dentro de un {@link HolidayCalendar}. Intra-módulo: {@code @ManyToOne} normal. */
@Entity
@Table(name = "holidays")
@Getter
@Setter
public class Holiday extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holiday_calendar_id", nullable = false, updatable = false)
    private HolidayCalendar holidayCalendar;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 160)
    private String name;
}
