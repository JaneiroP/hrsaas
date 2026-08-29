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

import java.math.BigDecimal;

/**
 * Saldo corriente de un empleado para un {@link LeaveType} — una fila por
 * combinación, no un histórico por año. El arrastre entre años es una
 * decisión de {@code LeaveBalanceService} al devengar (cuánto de lo
 * disponible sobrevive al corte), no una partición de esta tabla.
 */
@Entity
@Table(name = "leave_balances")
@Getter
@Setter
public class LeaveBalance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false, updatable = false)
    private LeaveType leaveType;

    @Column(name = "accrued_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal accruedDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    /** {@code accruedDays - usedDays}, mantenido por la capa de aplicación, no una columna generada. */
    @Column(name = "available_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal availableDays = BigDecimal.ZERO;
}
