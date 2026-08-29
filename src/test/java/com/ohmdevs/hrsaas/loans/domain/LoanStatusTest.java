package com.ohmdevs.hrsaas.loans.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoanStatusTest {

    @Test
    @DisplayName("ACTIVE pasa a PAID_OFF o CANCELLED")
    void activeTransitions() {
        assertThat(LoanStatus.ACTIVE.allowedTargets())
                .containsExactlyInAnyOrder(LoanStatus.PAID_OFF, LoanStatus.CANCELLED);
    }

    @Test
    @DisplayName("PAID_OFF y CANCELLED son estados finales")
    void terminalStates() {
        assertThat(LoanStatus.PAID_OFF.allowedTargets()).isEmpty();
        assertThat(LoanStatus.CANCELLED.allowedTargets()).isEmpty();
    }
}
