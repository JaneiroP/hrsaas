package com.ohmdevs.hrsaas.dataio.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estado de una {@link ImportRow} tras la validación (blueprint §07: "los errores se acumulan por fila"). */
public enum ImportRowStatus {
    PENDING,
    VALID,
    INVALID;

    public boolean canTransitionTo(ImportRowStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<ImportRowStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(VALID, INVALID);
            case VALID, INVALID -> EnumSet.noneOf(ImportRowStatus.class);
        };
    }
}
