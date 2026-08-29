package com.ohmdevs.hrsaas.payroll.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz completa de transiciones de {@link PayrollRunStatus} (AD-06). Sin base de datos. */
class PayrollRunStatusTest {

    @Test
    @DisplayName("BORRADOR pasa a CALCULADA o ANULADA")
    void borradorTransitions() {
        assertThat(PayrollRunStatus.BORRADOR.allowedTargets())
                .containsExactlyInAnyOrder(PayrollRunStatus.CALCULADA, PayrollRunStatus.ANULADA);
    }

    @Test
    @DisplayName("CALCULADA pasa a EN_REVISION o ANULADA")
    void calculadaTransitions() {
        assertThat(PayrollRunStatus.CALCULADA.allowedTargets())
                .containsExactlyInAnyOrder(PayrollRunStatus.EN_REVISION, PayrollRunStatus.ANULADA);
    }

    @Test
    @DisplayName("EN_REVISION pasa a APROBADA, vuelve a BORRADOR para recalcular, o se anula")
    void enRevisionTransitions() {
        assertThat(PayrollRunStatus.EN_REVISION.allowedTargets())
                .containsExactlyInAnyOrder(PayrollRunStatus.APROBADA, PayrollRunStatus.BORRADOR, PayrollRunStatus.ANULADA);
    }

    @Test
    @DisplayName("APROBADA solo pasa a PAGADA — ANULADA ya no es legal despues de aprobar")
    void aprobadaTransitions() {
        assertThat(PayrollRunStatus.APROBADA.allowedTargets())
                .containsExactly(PayrollRunStatus.PAGADA);
        assertThat(PayrollRunStatus.APROBADA.canTransitionTo(PayrollRunStatus.ANULADA)).isFalse();
    }

    @Test
    @DisplayName("PAGADA solo pasa a CERRADA")
    void pagadaTransitions() {
        assertThat(PayrollRunStatus.PAGADA.allowedTargets())
                .containsExactly(PayrollRunStatus.CERRADA);
    }

    @Test
    @DisplayName("CERRADA y ANULADA son estados finales (AD-03): sin destinos legales")
    void terminalStatesHaveNoLegalTargets() {
        assertThat(PayrollRunStatus.CERRADA.allowedTargets()).isEmpty();
        assertThat(PayrollRunStatus.ANULADA.allowedTargets()).isEmpty();
        for (PayrollRunStatus target : EnumSet.allOf(PayrollRunStatus.class)) {
            assertThat(PayrollRunStatus.CERRADA.canTransitionTo(target)).isFalse();
            assertThat(PayrollRunStatus.ANULADA.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("ningun estado se incluye a si mismo entre sus propios destinos")
    void noSelfTransitions() {
        for (PayrollRunStatus status : EnumSet.allOf(PayrollRunStatus.class)) {
            assertThat(status.allowedTargets()).doesNotContain(status);
        }
    }
}
