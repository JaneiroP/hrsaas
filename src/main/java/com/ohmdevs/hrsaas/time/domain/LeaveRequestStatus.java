package com.ohmdevs.hrsaas.time.domain;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de una {@link LeaveRequest} (AD-06). */
public enum LeaveRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Retirada por el empleado, desde {@code PENDING} o desde {@code APPROVED} si aún no empezó. */
    CANCELLED;

    public boolean canTransitionTo(LeaveRequestStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<LeaveRequestStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(CANCELLED);
            case REJECTED, CANCELLED -> EnumSet.noneOf(LeaveRequestStatus.class);
        };
    }
}
