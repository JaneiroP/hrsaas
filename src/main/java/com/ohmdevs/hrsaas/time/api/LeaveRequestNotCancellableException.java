package com.ohmdevs.hrsaas.time.api;

import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;

/**
 * La solicitud existe y es del empleado, pero ya no está {@code PENDING}.
 *
 * <p>El autoservicio del portal solo permite cancelar mientras esté
 * pendiente (blueprint §09), aunque la máquina de estados subyacente
 * ({@link LeaveRequestStatus#allowedTargets()}) también deja cancelar una
 * solicitud ya {@code APPROVED} — esa vía más amplia queda reservada a un
 * flujo de RRHH que todavía no existe, no al autoservicio.
 */
public class LeaveRequestNotCancellableException extends RuntimeException {

    public LeaveRequestNotCancellableException(Long requestId, LeaveRequestStatus currentStatus) {
        super("Solicitud " + requestId + " está en estado " + currentStatus + ", solo se cancela desde PENDING");
    }
}
