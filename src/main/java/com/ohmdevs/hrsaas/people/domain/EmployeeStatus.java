package com.ohmdevs.hrsaas.people.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida del expediente (AD-06), primer consumidor real del patrón
 * {@code canTransitionTo}. Lo dispara {@code people.api.EmployeeLifecycleService},
 * llamado desde {@code contracts} — el expediente no decide su propio estado,
 * lo decide la existencia de un contrato.
 */
public enum EmployeeStatus {
    /** Expediente en alta, sin contrato confirmado todavía. */
    PENDING,
    /** Tiene al menos un contrato {@code ACTIVE} o {@code SUSPENDED}. */
    ACTIVE,
    /** Sin contrato vigente; se conserva por posible recontratación. */
    INACTIVE;

    public boolean canTransitionTo(EmployeeStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<EmployeeStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACTIVE, INACTIVE);
            case ACTIVE -> EnumSet.of(INACTIVE);
            case INACTIVE -> EnumSet.of(ACTIVE);
        };
    }
}
