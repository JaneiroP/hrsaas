package com.ohmdevs.hrsaas.platform.domain;

/** Estado de una invitación. Solo PENDING es aceptable. */
public enum InvitationStatus {

    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}
