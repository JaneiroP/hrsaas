package com.ohmdevs.hrsaas.portal.web;

import com.ohmdevs.hrsaas.portal.application.NoEmployeeRecordException;
import com.ohmdevs.hrsaas.time.api.InsufficientLeaveBalanceException;
import com.ohmdevs.hrsaas.time.api.InvalidLeaveRangeException;
import com.ohmdevs.hrsaas.time.api.LeaveRequestNotCancellableException;
import com.ohmdevs.hrsaas.time.api.LeaveRequestNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Traduce a RFC 9457 las excepciones propias de {@code portal} y las de
 * {@code time.api} sobre licencias — viven aquí y no en
 * {@code shared.web.GlobalExceptionHandler} porque importan tipos de
 * módulos de negocio, algo que {@code shared} tiene prohibido (regla de
 * dirección).
 *
 * <p>Los códigos elegidos replican el contrato ya acordado con el mock del
 * frontend ({@code mock-api.interceptor.ts}): 422 para un rango de fechas
 * inválido, 409 para saldo insuficiente o para cancelar algo que ya no está
 * pendiente, 404 para un id ajeno o inexistente.
 */
@RestControllerAdvice
public class PortalExceptionHandler {

    @ExceptionHandler(NoEmployeeRecordException.class)
    public ProblemDetail handleNoEmployeeRecord(NoEmployeeRecordException ex) {
        return problem(HttpStatus.CONFLICT, "Sin expediente de empleado", ex.getMessage());
    }

    @ExceptionHandler(InvalidLeaveRangeException.class)
    public ProblemDetail handleInvalidRange(InvalidLeaveRangeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Rango de fechas inválido", ex.getMessage());
    }

    @ExceptionHandler(InsufficientLeaveBalanceException.class)
    public ProblemDetail handleInsufficientBalance(InsufficientLeaveBalanceException ex) {
        return problem(HttpStatus.CONFLICT, "Saldo insuficiente", ex.getMessage());
    }

    @ExceptionHandler(LeaveRequestNotFoundException.class)
    public ProblemDetail handleNotFound(LeaveRequestNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Solicitud no encontrada", ex.getMessage());
    }

    @ExceptionHandler(LeaveRequestNotCancellableException.class)
    public ProblemDetail handleNotCancellable(LeaveRequestNotCancellableException ex) {
        return problem(HttpStatus.CONFLICT, "No se puede cancelar", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty("traceId", UUID.randomUUID().toString());
        return problemDetail;
    }
}
