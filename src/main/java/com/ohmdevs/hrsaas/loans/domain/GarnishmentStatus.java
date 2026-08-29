package com.ohmdevs.hrsaas.loans.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de un {@link GarnishmentOrder} (AD-06). */
public enum GarnishmentStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(GarnishmentStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<GarnishmentStatus> allowedTargets() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(GarnishmentStatus.class);
        };
    }
}
