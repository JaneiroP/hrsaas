package com.ohmdevs.hrsaas.payroll.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Una línea del volante, con trazabilidad de qué concepto y qué regla la
 * produjo (blueprint §04: "toda cifra debe ser explicable"). {@code amount}
 * viene con signo: positivo para {@code EARNING}, negativo para
 * {@code DEDUCTION} — sumar todas las líneas de un volante da el neto
 * directamente, sin lógica condicional adicional.
 *
 * <p>{@code payrollConceptId} es nulo para líneas estructurales que no vienen
 * de un {@link PayrollConcept} configurado por el tenant: el salario base
 * (siempre el primero, tomado de {@code contracts}, no de una fórmula) y las
 * deducciones de {@code loans :: api} (cuota de préstamo, avance, embargo —
 * cada una atada a su propia instancia, no a un concepto reutilizable). La
 * {@code description} de la línea es la única trazabilidad para esos casos.
 */
@Entity
@Table(name = "payslip_lines")
@Getter
@Setter
public class PayslipLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false, updatable = false)
    private Payslip payslip;

    @Column(name = "payroll_concept_id", updatable = false)
    private Long payrollConceptId;

    @Column(nullable = false, length = 160)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PayrollConceptType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;
}
