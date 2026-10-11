package com.ohmdevs.hrsaas.integrations.web;

import com.ohmdevs.hrsaas.integrations.application.WebhookRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Traduce las excepciones propias de {@code integrations} a RFC 9457. Vive
 * aquí y no en {@code shared.web.GlobalExceptionHandler} por el mismo motivo
 * que {@code platform.web.PlatformExceptionHandler}: {@code shared} no puede
 * importar tipos de un módulo de negocio.
 */
@RestControllerAdvice
public class IntegrationsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IntegrationsExceptionHandler.class);

    /** 401 sin distinguir la causa: ver Javadoc de la excepción. */
    @ExceptionHandler(WebhookRejectedException.class)
    public ProblemDetail handleWebhookRejected(WebhookRejectedException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Webhook rechazado, traceId={}: {}", traceId, ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "La petición no se pudo autenticar.");
        problem.setTitle("Webhook rechazado");
        problem.setProperty("traceId", traceId);
        return problem;
    }
}
