package com.ohmdevs.hrsaas.banking.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un {@link PaymentBatchItem} (blueprint §06):
 * <pre>
 * PENDIENTE → ACEPTADO | RECHAZADO(código)
 * </pre>
 * Un ítem puede nacer ya {@code RECHAZADO} (sin pasar por {@code PENDIENTE})
 * cuando la validación bloqueante falla al construir el lote — p. ej. el
 * empleado no tiene cuenta bancaria principal — así queda reportado sin
 * bloquear el resto del lote.
 */
public enum PaymentItemStatus {
    PENDIENTE,
    ACEPTADO,
    RECHAZADO;

    public boolean canTransitionTo(PaymentItemStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<PaymentItemStatus> allowedTargets() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(ACEPTADO, RECHAZADO);
            case ACEPTADO, RECHAZADO -> EnumSet.noneOf(PaymentItemStatus.class);
        };
    }
}
