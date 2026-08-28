package com.ohmdevs.hrsaas.platform.access;

/**
 * El actor no tiene el permiso, o lo tiene con un alcance más estrecho del que
 * la operación exige.
 *
 * <p>El mensaje nombra el permiso y el alcance porque va a los logs, no al
 * cliente. Hacia afuera esto se traduce a un 403 sin detalle: decirle a quien
 * sondea exactamente qué permiso le falta es entregarle el mapa.
 */
public class PermissionDeniedException extends RuntimeException {

    private final String permissionCode;
    private final PermissionScope required;
    private final PermissionScope granted;

    public PermissionDeniedException(String permissionCode, PermissionScope required, PermissionScope granted) {
        super("Permiso '%s' denegado: exige alcance %s, concedido %s"
                .formatted(permissionCode, required, granted == null ? "ninguno" : granted));
        this.permissionCode = permissionCode;
        this.required = required;
        this.granted = granted;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public PermissionScope getRequired() {
        return required;
    }

    public PermissionScope getGranted() {
        return granted;
    }
}
