package com.ohmdevs.hrsaas.banking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz completa de transiciones de {@link PaymentBatchStatus} (AD-06). Sin base de datos. */
class PaymentBatchStatusTest {

    @Test
    @DisplayName("cada estado intermedio solo pasa al siguiente de la cadena lineal")
    void linearChainOfTransitions() {
        assertThat(PaymentBatchStatus.CONSTRUIDO.allowedTargets()).containsExactly(PaymentBatchStatus.VALIDADO);
        assertThat(PaymentBatchStatus.VALIDADO.allowedTargets()).containsExactly(PaymentBatchStatus.APROBADO);
        assertThat(PaymentBatchStatus.APROBADO.allowedTargets()).containsExactly(PaymentBatchStatus.EXPORTADO);
        assertThat(PaymentBatchStatus.EXPORTADO.allowedTargets()).containsExactly(PaymentBatchStatus.ENVIADO);
        assertThat(PaymentBatchStatus.ENVIADO.allowedTargets()).containsExactly(PaymentBatchStatus.CONCILIADO);
    }

    @Test
    @DisplayName("CONCILIADO es un estado final: sin destinos legales")
    void conciliadoIsFinal() {
        assertThat(PaymentBatchStatus.CONCILIADO.allowedTargets()).isEmpty();
        for (PaymentBatchStatus target : EnumSet.allOf(PaymentBatchStatus.class)) {
            assertThat(PaymentBatchStatus.CONCILIADO.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("no hay saltos hacia atras ni caminos que se salten un paso")
    void noBackwardsOrSkippedTransitions() {
        assertThat(PaymentBatchStatus.APROBADO.canTransitionTo(PaymentBatchStatus.CONSTRUIDO)).isFalse();
        assertThat(PaymentBatchStatus.CONSTRUIDO.canTransitionTo(PaymentBatchStatus.APROBADO)).isFalse();
        assertThat(PaymentBatchStatus.ENVIADO.canTransitionTo(PaymentBatchStatus.VALIDADO)).isFalse();
    }

    @Test
    @DisplayName("ningun estado se incluye a si mismo entre sus propios destinos")
    void noSelfTransitions() {
        for (PaymentBatchStatus status : EnumSet.allOf(PaymentBatchStatus.class)) {
            assertThat(status.allowedTargets()).doesNotContain(status);
        }
    }
}
