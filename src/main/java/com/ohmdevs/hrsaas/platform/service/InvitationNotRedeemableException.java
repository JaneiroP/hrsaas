package com.ohmdevs.hrsaas.platform.service;

/**
 * La invitación no existe, ya se usó, fue revocada o venció.
 *
 * <p>Un solo tipo de error para los cuatro casos, y a propósito: distinguirlos
 * hacia afuera le diría a quien prueba tokens al azar cuáles existieron alguna
 * vez. El detalle va al log, no a la respuesta.
 */
public class InvitationNotRedeemableException extends RuntimeException {

    public InvitationNotRedeemableException(String message) {
        super(message);
    }
}
