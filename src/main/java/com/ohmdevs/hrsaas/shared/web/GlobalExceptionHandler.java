package com.ohmdevs.hrsaas.shared.web;

import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Traduce las excepciones que cualquier módulo puede lanzar a RFC 9457
 * ({@code application/problem+json}, blueprint §08), con un {@code traceId}
 * por respuesta para correlacionar el reporte de un usuario con los logs.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para heredar gratis el
 * mapeo de las excepciones estándar de Spring MVC (validación de
 * {@code @Valid}, cuerpo ilegible, método no soportado...) a
 * {@link ProblemDetail} — no hay que reescribirlas aquí.
 *
 * <p><strong>Vive en {@code shared} (módulo {@code OPEN}) a propósito.</strong>
 * Los tipos que reconoce ({@code IllegalArgumentException},
 * {@code IllegalStateException}, {@code IllegalStateTransitionException} —
 * ya de {@code shared.statemachine} —, {@code EntityNotFoundException}) son
 * los que cualquier servicio de aplicación del proyecto ya lanza hoy, sin
 * conocimiento de un módulo de negocio concreto. Una excepción específica de
 * un módulo (p. ej. {@code PermissionDeniedException} de
 * {@code platform.access}, o las de {@code time.api} sobre licencias) se
 * traduce en un {@code @RestControllerAdvice} propio de quien sí puede
 * importar ese tipo — {@code platform.web}, {@code portal.web} — nunca aquí,
 * porque hacerlo obligaría a {@code shared} a depender de un módulo de
 * negocio (regla de dirección: {@code shared} no conoce ninguno).
 *
 * <p>Spring resuelve el handler más específico de entre <em>todos</em> los
 * {@code @RestControllerAdvice} del contexto, sin importar en qué clase
 * viva: un {@code @ExceptionHandler} para una subclase concreta en otro
 * advice gana sobre el de aquí para su supertipo, así que subclasificar
 * {@code IllegalArgumentException} en otro módulo para pedir un código
 * distinto de 400 es seguro y no requiere tocar esta clase.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Argumento inválido o precondición de negocio no cumplida — el tipo que
     * más servicios de aplicación del proyecto lanzan hoy (contrato
     * inexistente, documento duplicado, saldo insuficiente...).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex);
    }

    /** Transición ilegal de una máquina de estados (AD-06): el recurso existe, pero no en el estado que la operación exige. */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail handleIllegalStateTransition(IllegalStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "Transición no permitida", ex);
    }

    /** Estado del sistema incompatible con la operación (p. ej. contexto de tenant ausente en un punto que lo exige). */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Operación no disponible", ex);
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", ex);
    }

    /**
     * Red de seguridad final: nunca se le devuelve el mensaje ni la traza a
     * quien llama — podría ser una consulta SQL, una ruta de archivo o el
     * nombre de un secreto de configuración. Solo el {@code traceId} conecta
     * la respuesta con el log real, donde sí queda la excepción completa.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error no controlado, traceId={}", traceId, ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Referencia: " + traceId);
        problemDetail.setTitle("Error interno");
        problemDetail.setProperty("traceId", traceId);
        return problemDetail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("{}: {}, traceId={}", title, ex.getMessage(), traceId);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(title);
        problemDetail.setProperty("traceId", traceId);
        return problemDetail;
    }
}
