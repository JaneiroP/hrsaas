package com.ohmdevs.hrsaas.dataio.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz completa de transiciones de {@link ImportBatchStatus} (AD-06). Sin base de datos. */
class ImportBatchStatusTest {

    @Test
    @DisplayName("STAGED solo pasa a VALIDATED, VALIDATED solo a COMMITTED")
    void linearChain() {
        assertThat(ImportBatchStatus.STAGED.allowedTargets()).containsExactly(ImportBatchStatus.VALIDATED);
        assertThat(ImportBatchStatus.VALIDATED.allowedTargets()).containsExactly(ImportBatchStatus.COMMITTED);
    }

    @Test
    @DisplayName("COMMITTED es un estado final: sin destinos legales")
    void committedIsFinal() {
        assertThat(ImportBatchStatus.COMMITTED.allowedTargets()).isEmpty();
        for (ImportBatchStatus target : EnumSet.allOf(ImportBatchStatus.class)) {
            assertThat(ImportBatchStatus.COMMITTED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("no se puede saltar de STAGED directo a COMMITTED")
    void noSkippingValidation() {
        assertThat(ImportBatchStatus.STAGED.canTransitionTo(ImportBatchStatus.COMMITTED)).isFalse();
    }
}
