package com.ohmdevs.hrsaas.contracts.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida del contrato (AD-06), segundo consumidor del patrón {@code canTransitionTo}. */
public enum ContractStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    public boolean canTransitionTo(ContractStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<ContractStatus> allowedTargets() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(SUSPENDED, TERMINATED);
            case SUSPENDED -> EnumSet.of(ACTIVE, TERMINATED);
            case TERMINATED -> EnumSet.noneOf(ContractStatus.class);
        };
    }
}
