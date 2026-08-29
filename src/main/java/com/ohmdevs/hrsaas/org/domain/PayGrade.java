package com.ohmdevs.hrsaas.org.domain;

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

import java.math.BigDecimal;

/**
 * Banda salarial de una {@link Company}: rango mínimo/máximo de referencia
 * para un {@link Position}.
 *
 * <p>{@code BigDecimal}/{@code NUMERIC(15,2)} desnudo, sin el value object
 * {@code Money} (AD-05): introducirlo aquí sería prematuro sin el resto del
 * motor de nómina, que llega en Fase 3.
 */
@Entity
@Table(name = "pay_grades")
@Getter
@Setter
public class PayGrade extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "min_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxSalary;

    @Column(nullable = false, length = 3)
    private String currency = "DOP";

    @Column(nullable = false)
    private boolean active = true;
}
