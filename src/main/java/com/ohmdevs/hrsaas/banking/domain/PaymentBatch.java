package com.ohmdevs.hrsaas.banking.domain;

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
import java.time.Instant;

/**
 * Un lote de pago agrupado por banco y cuenta origen, construido a partir
 * de una {@code PayrollRun} aprobada (blueprint §06). {@code totalAmount}
 * es la suma de los ítems realmente pagables (excluye los nacidos
 * {@code RECHAZADO} por falta de cuenta bancaria) — no la suma de todos los
 * netos de la corrida.
 */
@Entity
@Table(name = "payment_batches")
@Getter
@Setter
public class PaymentBatch extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_run_id", nullable = false, updatable = false)
    private Long payrollRunId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "bank_name", nullable = false, length = 160)
    private String bankName;

    @Column(name = "origin_account_number", nullable = false, length = 60)
    private String originAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentBatchStatus status = PaymentBatchStatus.CONSTRUIDO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "built_by_user_id", nullable = false, updatable = false)
    private Long builtByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "exported_at")
    private Instant exportedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;
}
