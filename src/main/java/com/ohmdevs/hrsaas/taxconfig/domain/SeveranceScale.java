package com.ohmdevs.hrsaas.taxconfig.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tramo de la escala de cesantía por antigüedad (Código de Trabajo
 * dominicano), vigente por fecha. <strong>Sin {@code tenant_id}</strong> —
 * ver {@code package-info}.
 *
 * <p>{@code maxMonthsOfService} nulo significa "sin tope superior", el
 * último tramo. El cálculo real de cuánto le corresponde a un empleado
 * (aplicar el tramo correcto según su antigüedad y su salario) es de
 * {@code payroll}/{@code contracts} en Fase 3+; esta tabla solo guarda la
 * escala.
 */
@Entity
@Table(name = "severance_scales")
@Getter
@Setter
public class SeveranceScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_months_of_service", nullable = false)
    private int minMonthsOfService;

    @Column(name = "max_months_of_service")
    private Integer maxMonthsOfService;

    @Column(name = "days_of_salary_per_year", nullable = false, precision = 6, scale = 2)
    private BigDecimal daysOfSalaryPerYear;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
