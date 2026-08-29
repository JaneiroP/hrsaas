package com.ohmdevs.hrsaas.loans.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estado de una cuota individual de {@link LoanInstallment}. */
public enum LoanInstallmentStatus {
    PENDING,
    PAID;

    public boolean canTransitionTo(LoanInstallmentStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<LoanInstallmentStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(PAID);
            case PAID -> EnumSet.noneOf(LoanInstallmentStatus.class);
        };
    }
}
