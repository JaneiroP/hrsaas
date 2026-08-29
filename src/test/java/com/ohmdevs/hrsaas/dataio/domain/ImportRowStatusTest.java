package com.ohmdevs.hrsaas.dataio.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transiciones de {@link ImportRowStatus} (AD-06). Sin base de datos. */
class ImportRowStatusTest {

    @Test
    @DisplayName("PENDING pasa a VALID o INVALID")
    void pendingTransitions() {
        assertThat(ImportRowStatus.PENDING.allowedTargets())
                .containsExactlyInAnyOrder(ImportRowStatus.VALID, ImportRowStatus.INVALID);
    }

    @Test
    @DisplayName("VALID e INVALID son estados finales")
    void terminalStates() {
        assertThat(ImportRowStatus.VALID.allowedTargets()).isEmpty();
        assertThat(ImportRowStatus.INVALID.allowedTargets()).isEmpty();
    }
}
