package com.ohmdevs.hrsaas.loans.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de un {@link LoanAgreement} (AD-06). */
public enum LoanStatus {
    ACTIVE,
    PAID_OFF,
    CANCELLED;

    public boolean canTransitionTo(LoanStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<LoanStatus> allowedTargets() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(PAID_OFF, CANCELLED);
            case PAID_OFF, CANCELLED -> EnumSet.noneOf(LoanStatus.class);
        };
    }
}
