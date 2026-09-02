package com.ohmdevs.hrsaas.platform.service;

/**
 * Correo, contraseña o pertenencia inválidos al iniciar sesión.
 *
 * <p>Deliberadamente un solo tipo para las tres causas (usuario inexistente,
 * contraseña incorrecta, cuenta bloqueada o sin tenant activo): distinguirlas
 * hacia afuera le regalaría a un atacante la enumeración de correos
 * registrados. El detalle real, si existe, va al log — nunca a la respuesta.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
