package com.ohmdevs.hrsaas.time.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transiciones de {@link LeaveRequestStatus} (AD-06). Sin base de datos. */
class LeaveRequestStatusTest {

    @Test
    @DisplayName("PENDING pasa a APPROVED, REJECTED o CANCELLED")
    void pendingTransitions() {
        assertThat(LeaveRequestStatus.PENDING.allowedTargets())
                .containsExactlyInAnyOrder(LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED,
                        LeaveRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("APPROVED solo pasa a CANCELLED (retiro de una ausencia ya aprobada)")
    void approvedTransitions() {
        assertThat(LeaveRequestStatus.APPROVED.allowedTargets()).containsExactly(LeaveRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("REJECTED y CANCELLED son estados finales")
    void terminalStates() {
        assertThat(LeaveRequestStatus.REJECTED.allowedTargets()).isEmpty();
        assertThat(LeaveRequestStatus.CANCELLED.allowedTargets()).isEmpty();
    }

    @Test
    @DisplayName("ningun estado se incluye a si mismo entre sus propios destinos")
    void noSelfTransitions() {
        for (LeaveRequestStatus status : EnumSet.allOf(LeaveRequestStatus.class)) {
            assertThat(status.allowedTargets()).doesNotContain(status);
        }
    }
}
