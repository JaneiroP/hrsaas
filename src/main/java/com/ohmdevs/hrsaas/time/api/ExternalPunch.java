package com.ohmdevs.hrsaas.time.api;

import java.time.Instant;

/**
 * Un marcaje que llega de un sistema externo, ya resuelto a un empleado de
 * este tenant.
 *
 * @param deviceId identificador del dispositivo en el sistema de origen; con
 *                 {@code employeeId} y {@code occurredAt} forma la clave de
 *                 idempotencia (AD-11)
 */
public record ExternalPunch(Long employeeId, Direction direction, Instant occurredAt, String deviceId) {

    /** Entrada o salida. Los descansos no llegan por esta vía. */
    public enum Direction {
        IN,
        OUT
    }
}
