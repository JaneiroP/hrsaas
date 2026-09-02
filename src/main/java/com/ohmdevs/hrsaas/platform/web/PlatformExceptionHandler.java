package com.ohmdevs.hrsaas.platform.web;

import com.ohmdevs.hrsaas.platform.access.PermissionDeniedException;
import com.ohmdevs.hrsaas.platform.service.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Traduce las excepciones propias de {@code platform.access}/{@code platform.service}
 * a RFC 9457. Vive aquí y no en {@code shared.web.GlobalExceptionHandler}
 * porque importa tipos de {@code platform} — {@code shared} no puede depender
 * de ningún módulo de negocio (regla de dirección, ver Javadoc del handler
 * genérico).
 */
@RestControllerAdvice
public class PlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformExceptionHandler.class);

    /**
     * 403 sin detalle del permiso o alcance que faltó — el mensaje completo
     * (qué permiso, qué alcance exigía, cuál tenía) va al log, no a quien
     * hizo la petición, tal como documenta {@link PermissionDeniedException}.
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ProblemDetail handlePermissionDenied(PermissionDeniedException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Permiso denegado, traceId={}: {}", traceId, ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operación.");
        problem.setTitle("Acceso denegado");
        problem.setProperty("traceId", traceId);
        return problem;
    }

    /** 401 sin distinguir la causa (correo inexistente, contraseña incorrecta, cuenta bloqueada): ver Javadoc de la excepción. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Login rechazado, traceId={}: {}", traceId, ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        problem.setTitle("Credenciales inválidas");
        problem.setProperty("traceId", traceId);
        return problem;
    }
}
