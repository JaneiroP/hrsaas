package com.ohmdevs.hrsaas.payroll.domain;

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
 * El volante de un empleado dentro de una {@link PayrollRun}. Nunca se
 * edita en el sitio tras {@code CERRADA} (AD-03) — el snapshot en PDF
 * (pendiente, ver {@code package-info}) se archivaría desde esta fila, no
 * desde datos vivos.
 */
@Entity
@Table(name = "payslips")
@Getter
@Setter
public class Payslip extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false, updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    @Column(name = "gross_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    @Column(nullable = false, length = 3)
    private String currency;
}
