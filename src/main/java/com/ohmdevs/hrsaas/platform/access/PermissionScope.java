package com.ohmdevs.hrsaas.platform.access;

/**
 * Alcance por fila de un permiso: no <em>si</em> puede leer volantes, sino
 * <em>de quién</em>.
 *
 * <p>Un RBAC clásico responde la primera pregunta y deja la segunda al criterio
 * de quien escriba cada consulta. Ahí es donde se filtran los datos en un SaaS
 * de RRHH: el usuario tiene permiso legítimo de lectura y cambia un id en la URL.
 *
 * <p>El orden de {@link #breadth} es significativo y define la contención: un
 * alcance más amplio incluye a los más estrechos.
 */
public enum PermissionScope {

    /** Solo sus propios datos. */
    SELF(0),

    /** Su equipo, siguiendo la cadena de supervisión (incluye reportes indirectos). */
    TEAM(1),

    /** Toda una razón social a la que el actor esté asignado. */
    COMPANY(2),

    /** Todo el tenant. */
    ALL(3);

    private final int breadth;

    PermissionScope(int breadth) {
        this.breadth = breadth;
    }

    /** {@code true} si este alcance cubre todo lo que cubre {@code other}. */
    public boolean includes(PermissionScope other) {
        return this.breadth >= other.breadth;
    }

    /**
     * El más amplio de los dos. Se usa al consolidar varios roles: si un usuario
     * es supervisor (TEAM) y además analista de RRHH (COMPANY), sobre el mismo
     * permiso gana COMPANY.
     */
    public PermissionScope widest(PermissionScope other) {
        return this.breadth >= other.breadth ? this : other;
    }
}
