package com.ohmdevs.hrsaas.assets.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de una {@link AssetAssignment}. */
public enum AssetAssignmentStatus {
    ACTIVE,
    RETURNED;

    public boolean canTransitionTo(AssetAssignmentStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<AssetAssignmentStatus> allowedTargets() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(RETURNED);
            case RETURNED -> EnumSet.noneOf(AssetAssignmentStatus.class);
        };
    }
}
