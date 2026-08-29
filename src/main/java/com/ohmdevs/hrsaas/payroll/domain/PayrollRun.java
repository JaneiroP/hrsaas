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

import java.time.Instant;

/**
 * Una corrida sobre un {@link PayrollPeriod} (AD-03: libro mayor inmutable
 * una vez {@code CERRADA}). {@code configVersionId} es un identificador
 * arbitrario del snapshot de configuración usado — sin un sistema de
 * versionado de configuración real todavía, se guarda un marcador (p. ej.
 * un timestamp) para dejar la intención del campo lista; recalcular sin
 * saber qué versión se usó es exactamente el problema que el blueprint
 * advierte evitar.
 */
@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
public class PayrollRun extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false, updatable = false)
    private PayrollPeriod payrollPeriod;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayrollRunStatus status = PayrollRunStatus.BORRADOR;

    @Column(name = "config_version_id", length = 100)
    private String configVersionId;

    /** Si esta corrida es el reverso de otra, aquí va el id de la original — nunca al revés. */
    @Column(name = "reversal_of_run_id")
    private Long reversalOfRunId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
