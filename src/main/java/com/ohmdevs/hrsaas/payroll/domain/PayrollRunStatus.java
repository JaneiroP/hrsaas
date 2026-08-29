package com.ohmdevs.hrsaas.payroll.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de una corrida (AD-06, blueprint §04):
 * <pre>
 * BORRADOR → CALCULADA → EN_REVISION → APROBADA → PAGADA → CERRADA
 *    ↑___________________|  (devolver a borrador · recalcular)
 *               ↓ ANULADA (solo antes de aprobar)
 * </pre>
 * Desde {@code CERRADA} no existe transición hacia atrás (AD-03) — corregir
 * significa una corrida de reverso nueva, nunca reabrir ésta. El reverso no
 * es una transición de este enum: es {@code PayrollRunLifecycleService}
 * creando un {@link PayrollRun} distinto que referencia al original.
 */
public enum PayrollRunStatus {
    BORRADOR,
    CALCULADA,
    EN_REVISION,
    APROBADA,
    PAGADA,
    CERRADA,
    ANULADA;

    public boolean canTransitionTo(PayrollRunStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<PayrollRunStatus> allowedTargets() {
        return switch (this) {
            case BORRADOR -> EnumSet.of(CALCULADA, ANULADA);
            case CALCULADA -> EnumSet.of(EN_REVISION, ANULADA);
            case EN_REVISION -> EnumSet.of(APROBADA, BORRADOR, ANULADA);
            case APROBADA -> EnumSet.of(PAGADA);
            case PAGADA -> EnumSet.of(CERRADA);
            case CERRADA, ANULADA -> EnumSet.noneOf(PayrollRunStatus.class);
        };
    }
}
