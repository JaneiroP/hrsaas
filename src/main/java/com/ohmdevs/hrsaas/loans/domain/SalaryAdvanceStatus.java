package com.ohmdevs.hrsaas.loans.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de un {@link SalaryAdvance} (AD-06). */
public enum SalaryAdvanceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Ya se descontó en una corrida de nómina. */
    DEDUCTED;

    public boolean canTransitionTo(SalaryAdvanceStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<SalaryAdvanceStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(DEDUCTED);
            case REJECTED, DEDUCTED -> EnumSet.noneOf(SalaryAdvanceStatus.class);
        };
    }
}
