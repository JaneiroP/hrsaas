package com.ohmdevs.hrsaas.contracts.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transiciones de {@link ContractStatus} (AD-06). Sin base de datos. */
class ContractStatusTest {

    @Test
    @DisplayName("ACTIVE pasa a SUSPENDED o TERMINATED")
    void activeTransitions() {
        assertThat(ContractStatus.ACTIVE.allowedTargets())
                .containsExactlyInAnyOrder(ContractStatus.SUSPENDED, ContractStatus.TERMINATED);
    }

    @Test
    @DisplayName("SUSPENDED vuelve a ACTIVE o pasa a TERMINATED")
    void suspendedTransitions() {
        assertThat(ContractStatus.SUSPENDED.allowedTargets())
                .containsExactlyInAnyOrder(ContractStatus.ACTIVE, ContractStatus.TERMINATED);
    }

    @Test
    @DisplayName("TERMINATED es un estado final: sin destinos legales")
    void terminatedIsFinal() {
        assertThat(ContractStatus.TERMINATED.allowedTargets()).isEmpty();
        assertThat(ContractStatus.TERMINATED.canTransitionTo(ContractStatus.ACTIVE)).isFalse();
        assertThat(ContractStatus.TERMINATED.canTransitionTo(ContractStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("ningun estado se incluye a si mismo entre sus propios destinos")
    void noSelfTransitions() {
        for (ContractStatus status : EnumSet.allOf(ContractStatus.class)) {
            assertThat(status.allowedTargets()).doesNotContain(status);
        }
    }
}
