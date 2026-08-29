package com.ohmdevs.hrsaas.assets.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de un {@link Asset} (AD-06). */
public enum AssetStatus {
    AVAILABLE,
    ASSIGNED,
    RETIRED;

    public boolean canTransitionTo(AssetStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<AssetStatus> allowedTargets() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(ASSIGNED, RETIRED);
            case ASSIGNED -> EnumSet.of(AVAILABLE, RETIRED);
            case RETIRED -> EnumSet.noneOf(AssetStatus.class);
        };
    }
}
