package com.ohmdevs.hrsaas.banking.domain;

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
 * Un ítem de un {@link PaymentBatch}: un empleado, un monto. {@code
 * bankAccountId} referencia la cuenta usada (por id, no por número) —
 * a propósito no se guarda una copia del número de cuenta en esta tabla:
 * eso crearía una segunda copia de un dato cifrado (AD-08) que además
 * quedaría desactualizada si la cuenta cambia. El generador de archivo
 * bancario (pendiente, ver {@code package-info}) deberá resolver el número
 * en el momento de generar, vía {@code people.api}.
 */
@Entity
@Table(name = "payment_batch_items")
@Getter
@Setter
public class PaymentBatchItem extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_batch_id", nullable = false, updatable = false)
    private PaymentBatch paymentBatch;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    @Column(name = "bank_account_id")
    private Long bankAccountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentItemStatus status = PaymentItemStatus.PENDIENTE;

    @Column(name = "rejection_code", length = 40)
    private String rejectionCode;

    @Column(name = "rejection_reason", length = 200)
    private String rejectionReason;
}
