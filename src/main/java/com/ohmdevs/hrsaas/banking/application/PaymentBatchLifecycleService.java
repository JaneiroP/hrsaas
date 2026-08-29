package com.ohmdevs.hrsaas.banking.application;

import com.ohmdevs.hrsaas.banking.domain.PaymentBatch;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchItem;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchStatus;
import com.ohmdevs.hrsaas.banking.domain.PaymentItemStatus;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchItemRepository;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchRepository;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * El ciclo de vida de un {@link PaymentBatch} (AD-06, blueprint §06). Cada
 * transición valida {@code status.canTransitionTo(target)} aquí, mismo
 * patrón que {@code PayrollRunLifecycleService}.
 */
@Service
public class PaymentBatchLifecycleService {

    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentBatchItemRepository paymentBatchItemRepository;

    public PaymentBatchLifecycleService(PaymentBatchRepository paymentBatchRepository,
                                         PaymentBatchItemRepository paymentBatchItemRepository) {
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentBatchItemRepository = paymentBatchItemRepository;
    }

    /**
     * {@code CONSTRUIDO → VALIDADO}: la suma de los ítems {@code PENDIENTE}
     * debe coincidir con {@code totalAmount} — invariante estructural, no
     * debería poder fallar salvo un error de programación, pero blueprint
     * §06 la marca explícitamente como bloqueante.
     */
    @Transactional
    public PaymentBatch validate(Long batchId) {
        PaymentBatch batch = getBatch(batchId);
        transition(batch, PaymentBatchStatus.VALIDADO);

        List<PaymentBatchItem> items = paymentBatchItemRepository.findByPaymentBatchId(batchId);
        BigDecimal pendingSum = items.stream()
                .filter(item -> item.getStatus() == PaymentItemStatus.PENDIENTE)
                .map(PaymentBatchItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pendingSum.compareTo(batch.getTotalAmount()) != 0) {
            throw new IllegalStateException(
                    "La suma de items pendientes (%s) no coincide con el total del lote (%s)"
                            .formatted(pendingSum, batch.getTotalAmount()));
        }

        return paymentBatchRepository.save(batch);
    }

    /** {@code VALIDADO → APROBADO}: maker-checker — quien aprueba no puede ser quien construyó. */
    @Transactional
    public PaymentBatch approve(Long batchId, Long approvingUserId) {
        PaymentBatch batch = getBatch(batchId);
        if (approvingUserId.equals(batch.getBuiltByUserId())) {
            throw new IllegalStateException(
                    "Maker-checker: el usuario " + approvingUserId + " construyo este lote y no puede aprobarlo");
        }
        transition(batch, PaymentBatchStatus.APROBADO);
        batch.setApprovedByUserId(approvingUserId);
        batch.setApprovedAt(Instant.now());
        return paymentBatchRepository.save(batch);
    }

    @Transactional
    public PaymentBatch markExported(Long batchId) {
        PaymentBatch batch = getBatch(batchId);
        transition(batch, PaymentBatchStatus.EXPORTADO);
        batch.setExportedAt(Instant.now());
        return paymentBatchRepository.save(batch);
    }

    @Transactional
    public PaymentBatch markSent(Long batchId) {
        PaymentBatch batch = getBatch(batchId);
        transition(batch, PaymentBatchStatus.ENVIADO);
        batch.setSentAt(Instant.now());
        return paymentBatchRepository.save(batch);
    }

    /**
     * {@code ENVIADO → CONCILIADO}: aplica los resultados ya interpretados
     * del archivo de retorno (ver {@link ReconciliationOutcome}) a cada
     * ítem {@code PENDIENTE}.
     */
    @Transactional
    public PaymentBatch reconcile(Long batchId, List<ReconciliationOutcome> outcomes) {
        PaymentBatch batch = getBatch(batchId);
        transition(batch, PaymentBatchStatus.CONCILIADO);

        for (ReconciliationOutcome outcome : outcomes) {
            PaymentBatchItem item = paymentBatchItemRepository.findById(outcome.paymentBatchItemId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "PaymentBatchItem no encontrado: " + outcome.paymentBatchItemId()));
            PaymentItemStatus target = outcome.accepted() ? PaymentItemStatus.ACEPTADO : PaymentItemStatus.RECHAZADO;
            if (!item.getStatus().canTransitionTo(target)) {
                throw new IllegalStateTransitionException("PaymentBatchItem", item.getId(), item.getStatus(), target);
            }
            item.setStatus(target);
            if (!outcome.accepted()) {
                item.setRejectionCode(outcome.rejectionCode());
                item.setRejectionReason(outcome.rejectionReason());
            }
            paymentBatchItemRepository.save(item);
        }

        batch.setReconciledAt(Instant.now());
        return paymentBatchRepository.save(batch);
    }

    private PaymentBatch getBatch(Long batchId) {
        return paymentBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("PaymentBatch no encontrado: " + batchId));
    }

    private void transition(PaymentBatch batch, PaymentBatchStatus target) {
        if (!batch.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException("PaymentBatch", batch.getId(), batch.getStatus(), target);
        }
        batch.setStatus(target);
    }
}
