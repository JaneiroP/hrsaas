package com.ohmdevs.hrsaas.banking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transiciones de {@link PaymentItemStatus} (AD-06). Sin base de datos. */
class PaymentItemStatusTest {

    @Test
    @DisplayName("PENDIENTE pasa a ACEPTADO o RECHAZADO")
    void pendienteTransitions() {
        assertThat(PaymentItemStatus.PENDIENTE.allowedTargets())
                .containsExactlyInAnyOrder(PaymentItemStatus.ACEPTADO, PaymentItemStatus.RECHAZADO);
    }

    @Test
    @DisplayName("ACEPTADO y RECHAZADO son estados finales: sin destinos legales")
    void terminalStatesHaveNoLegalTargets() {
        assertThat(PaymentItemStatus.ACEPTADO.allowedTargets()).isEmpty();
        assertThat(PaymentItemStatus.RECHAZADO.allowedTargets()).isEmpty();
        assertThat(PaymentItemStatus.ACEPTADO.canTransitionTo(PaymentItemStatus.RECHAZADO)).isFalse();
        assertThat(PaymentItemStatus.RECHAZADO.canTransitionTo(PaymentItemStatus.ACEPTADO)).isFalse();
    }
}
