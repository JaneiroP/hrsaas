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
 * Tipo de licencia configurable por tenant (vacaciones, licencia médica,
 * permiso sin sueldo...), con sus reglas de devengo, tope y arrastre.
 *
 * <p>{@code accrualDaysPerYear} nulo significa que este tipo no devenga
 * saldo (p. ej. licencia sin sueldo): se solicita y se aprueba, pero no hay
 * {@link LeaveBalance} que descontar ni verificar.
 */
@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "accrual_days_per_year", precision = 6, scale = 2)
    private BigDecimal accrualDaysPerYear;

    /** Días que sobreviven al cambio de año. Nulo = sin tope explícito; 0 = no se arrastra nada. */
    @Column(name = "max_carryover_days")
    private Integer maxCarryoverDays;

    @Column(nullable = false)
    private boolean active = true;
}
