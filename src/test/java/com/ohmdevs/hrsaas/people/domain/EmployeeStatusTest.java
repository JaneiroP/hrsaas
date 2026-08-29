package com.ohmdevs.hrsaas.people.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transiciones de {@link EmployeeStatus} (AD-06). Sin base de datos. */
class EmployeeStatusTest {

    @Test
    @DisplayName("PENDING pasa a ACTIVE o INACTIVE, nunca a si mismo")
    void pendingTransitions() {
        assertThat(EmployeeStatus.PENDING.allowedTargets())
                .containsExactlyInAnyOrder(EmployeeStatus.ACTIVE, EmployeeStatus.INACTIVE);
        assertThat(EmployeeStatus.PENDING.canTransitionTo(EmployeeStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE solo pasa a INACTIVE")
    void activeTransitions() {
        assertThat(EmployeeStatus.ACTIVE.allowedTargets()).containsExactly(EmployeeStatus.INACTIVE);
        assertThat(EmployeeStatus.ACTIVE.canTransitionTo(EmployeeStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("INACTIVE solo pasa a ACTIVE (recontratacion)")
    void inactiveTransitions() {
        assertThat(EmployeeStatus.INACTIVE.allowedTargets()).containsExactly(EmployeeStatus.ACTIVE);
        assertThat(EmployeeStatus.INACTIVE.canTransitionTo(EmployeeStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("todo estado tiene al menos un destino legal declarado")
    void everyStateHasSomeAllowedTarget() {
        for (EmployeeStatus status : EmployeeStatus.values()) {
            assertThat(status.allowedTargets()).isNotEmpty();
        }
        // Y ninguno se incluye a si mismo entre sus propios destinos.
        for (EmployeeStatus status : EnumSet.allOf(EmployeeStatus.class)) {
            assertThat(status.allowedTargets()).doesNotContain(status);
        }
    }
}
