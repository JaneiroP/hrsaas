package com.ohmdevs.hrsaas.assets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetStatusTest {

    @Test
    @DisplayName("AVAILABLE pasa a ASSIGNED o RETIRED")
    void availableTransitions() {
        assertThat(AssetStatus.AVAILABLE.allowedTargets())
                .containsExactlyInAnyOrder(AssetStatus.ASSIGNED, AssetStatus.RETIRED);
    }

    @Test
    @DisplayName("ASSIGNED vuelve a AVAILABLE (devolucion) o pasa a RETIRED")
    void assignedTransitions() {
        assertThat(AssetStatus.ASSIGNED.allowedTargets())
                .containsExactlyInAnyOrder(AssetStatus.AVAILABLE, AssetStatus.RETIRED);
    }

    @Test
    @DisplayName("RETIRED es un estado final")
    void retiredIsFinal() {
        assertThat(AssetStatus.RETIRED.allowedTargets()).isEmpty();
    }
}
