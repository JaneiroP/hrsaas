package com.ohmdevs.hrsaas.banking.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un {@link PaymentBatch} (AD-06, blueprint §06):
 * <pre>
 * CONSTRUIDO → VALIDADO → APROBADO → EXPORTADO → ENVIADO → CONCILIADO
 * </pre>
 * Estrictamente lineal — el blueprint no contempla un camino de vuelta ni
 * una anulación a nivel de lote; el rechazo vive a nivel de ítem
 * ({@link PaymentItemStatus}), no de lote completo.
 */
public enum PaymentBatchStatus {
    CONSTRUIDO,
    VALIDADO,
    APROBADO,
    EXPORTADO,
    ENVIADO,
    CONCILIADO;

    public boolean canTransitionTo(PaymentBatchStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<PaymentBatchStatus> allowedTargets() {
        return switch (this) {
            case CONSTRUIDO -> EnumSet.of(VALIDADO);
            case VALIDADO -> EnumSet.of(APROBADO);
            case APROBADO -> EnumSet.of(EXPORTADO);
            case EXPORTADO -> EnumSet.of(ENVIADO);
            case ENVIADO -> EnumSet.of(CONCILIADO);
            case CONCILIADO -> EnumSet.noneOf(PaymentBatchStatus.class);
        };
    }
}
