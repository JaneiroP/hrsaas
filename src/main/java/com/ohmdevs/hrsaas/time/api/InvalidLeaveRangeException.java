package com.ohmdevs.hrsaas.time.api;

/**
 * El rango de fechas de una solicitud de licencia no es procesable: fin
 * anterior a inicio, o un rango que no contiene ni un solo día hábil.
 */
public class InvalidLeaveRangeException extends IllegalArgumentException {

    public InvalidLeaveRangeException(String message) {
        super(message);
    }
}
