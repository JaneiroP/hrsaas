package com.ohmdevs.hrsaas.platform.domain;

/** Estado de la pertenencia de un usuario a un tenant. */
public enum MembershipStatus {

    ACTIVE,

    /** Bloqueado temporalmente; conserva roles. */
    SUSPENDED,

    /** Salida definitiva. No se borra la fila: el histórico de auditoría la referencia. */
    REVOKED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
