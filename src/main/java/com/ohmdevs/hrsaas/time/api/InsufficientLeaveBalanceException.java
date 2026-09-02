package com.ohmdevs.hrsaas.time.api;

/**
 * El empleado no tiene saldo suficiente del tipo de licencia solicitado.
 *
 * <p>Extiende {@link IllegalArgumentException} a propósito: es lo que ya
 * esperaba {@code LeaveManagementServiceTest} antes de que este tipo
 * existiera (la solicitud sigue siendo, en sentido amplio, un argumento
 * inválido), y le basta a {@code shared.web.GlobalExceptionHandler} para
 * caer en 400 si nadie pide algo más específico. {@code portal} sí lo pide:
 * mapea este tipo puntual a 409 (conflicto de estado — el saldo — no un
 * cuerpo de petición mal formado), y Spring resuelve el handler más
 * específico sin que las dos reglas choquen.
 */
public class InsufficientLeaveBalanceException extends IllegalArgumentException {

    public InsufficientLeaveBalanceException(String message) {
        super(message);
    }
}
