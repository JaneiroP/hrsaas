package com.ohmdevs.hrsaas.time.api;

/**
 * No existe una solicitud de licencia con ese id <strong>perteneciente al
 * empleado que la pide</strong>. Deliberadamente el mismo resultado tanto si
 * el id no existe como si existe pero es de otro empleado — blueprint §09: el
 * riesgo no es que falte autenticación, es que un id ajeno confirme su propia
 * existencia.
 */
public class LeaveRequestNotFoundException extends RuntimeException {

    public LeaveRequestNotFoundException(Long requestId) {
        super("Solicitud de licencia " + requestId + " no existe para este empleado");
    }

    /** Para un id que ni siquiera es un número — mismo resultado hacia afuera que "no existe", ver Javadoc de la clase. */
    public LeaveRequestNotFoundException(String rawId) {
        super("Solicitud de licencia '" + rawId + "' no es un id válido");
    }
}
