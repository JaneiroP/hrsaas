package com.ohmdevs.hrsaas.platform.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un tenant, con transiciones declaradas (AD-06).
 *
 * <p>Las transiciones ilegales lanzan excepción en el servicio, no se validan
 * en el controlador.
 */
public enum TenantStatus {

    /** Creado, sin usuario propietario aún. No permite operar. */
    PENDING,

    /** Operativo. */
    ACTIVE,

    /** Suspendido por falta de pago o decisión administrativa. Datos intactos. */
    SUSPENDED;

    public boolean canTransitionTo(TenantStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<TenantStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACTIVE);
            case ACTIVE -> EnumSet.of(SUSPENDED);
            case SUSPENDED -> EnumSet.of(ACTIVE);
        };
    }

    /** Un tenant no operativo no debe poder autenticar ni calcular nada. */
    public boolean isOperational() {
        return this == ACTIVE;
    }
}
