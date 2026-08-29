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
 * Aportes patronales y provisiones, calculados en la misma corrida que las
 * líneas del volante porque comparten las mismas bases — pero
 * <strong>nunca afectan el neto a pagar</strong> (blueprint §04): son costo
 * patronal y pasivo laboral acumulado, no algo que el empleado ve descontado.
 */
@Entity
@Table(name = "accruals")
@Getter
@Setter
public class Accrual extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "concept_code", nullable = false, length = 32)
    private String conceptCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PayrollConceptType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
