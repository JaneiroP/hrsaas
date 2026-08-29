package com.ohmdevs.hrsaas.dataio.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un {@link ImportBatch} (AD-06, blueprint §07):
 * <pre>
 * STAGED → VALIDATED → COMMITTED
 * </pre>
 * Lineal, sin vuelta atrás — un lote mal cargado se descarta y se sube de
 * nuevo, no se corrige en el sitio (mismo espíritu de AD-03: nada de esto
 * se edita, se reintenta desde el origen).
 */
public enum ImportBatchStatus {
    STAGED,
    VALIDATED,
    COMMITTED;

    public boolean canTransitionTo(ImportBatchStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<ImportBatchStatus> allowedTargets() {
        return switch (this) {
            case STAGED -> EnumSet.of(VALIDATED);
            case VALIDATED -> EnumSet.of(COMMITTED);
            case COMMITTED -> EnumSet.noneOf(ImportBatchStatus.class);
        };
    }
}
