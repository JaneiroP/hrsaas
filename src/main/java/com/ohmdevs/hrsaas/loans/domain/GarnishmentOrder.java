package com.ohmdevs.hrsaas.loans.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Embargo judicial. Exactamente uno de {@code fixedAmount}/{@code percentage}
 * va con valor — lo exige la capa de aplicación, no una constraint de base
 * de datos, porque cuál de los dos aplica es una regla de negocio, no de
 * integridad.
 *
 * <p>{@code priority} decide el orden de prelación cuando varios embargos
 * compiten por el mínimo inembargable de un mismo empleado — ese cálculo es
 * de {@code payroll} (AD-04: nada de reglas fiscales aquí), esta entidad
 * solo registra el orden declarado en la orden judicial.
 */
@Entity
@Table(name = "garnishment_orders")
@Getter
@Setter
public class GarnishmentOrder extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "court_order_number", nullable = false, length = 100)
    private String courtOrderNumber;

    @Column(nullable = false, length = 200)
    private String beneficiary;

    @Column(name = "fixed_amount", precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    /** Porcentaje del neto, expresado 0-100. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(nullable = false)
    private int priority;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GarnishmentStatus status = GarnishmentStatus.ACTIVE;

    @Column(length = 500)
    private String notes;
}
