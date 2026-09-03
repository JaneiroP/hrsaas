package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.access.PermissionScope;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Roles creados en el aprovisionamiento de cada tenant.
 *
 * <p>Son el ejemplo trabajado de por qué el alcance vive en la concesión: mira
 * a {@link #SUPERVISOR}, que lee los expedientes de su equipo pero solo su
 * propio volante de pago. Con el alcance en el rol harían falta dos roles y una
 * regla en alguna parte para combinarlos.
 *
 * <p>El tenant puede editarlos y crear los suyos; esto es solo un punto de
 * partida sensato para no arrancar con una pantalla de permisos en blanco.
 */
public enum SystemRole {

    /** Todos los permisos del catálogo, cada uno al máximo alcance que admita. */
    OWNER("Propietario", "Control total del tenant", true, Map.of()),

    /**
     * Opera RRHH y nómina, pero <strong>no aprueba</strong>.
     *
     * <p>La ausencia de {@code payroll:approve} y {@code bank_batch:approve} es
     * intencionada: quien construye una nómina o un lote de pago no debe ser
     * quien lo aprueba. Ese maker-checker es una defensa contra el fraude, y
     * empieza aquí, en el rol por defecto — no en una casilla de configuración
     * que nadie marca.
     */
    HR_MANAGER("Gerente de RRHH", "Administra personal y ejecuta nómina", false, Map.ofEntries(
            // company:read/manage solo admiten ALL en el catálogo (V3): no hay
            // un alcance COMPANY intermedio con sentido para administrar la
            // propia lista de razones sociales — ver Javadoc de
            // org.web.OrgAdminController. Sin esto, HR_MANAGER no podía ver
            // ni armar la estructura (departamentos, cargos, centros de
            // costo) que después necesita para contratar a alguien.
            entry("company:read", PermissionScope.ALL),
            entry("company:manage", PermissionScope.ALL),
            entry("employee:read", PermissionScope.COMPANY),
            entry("employee:manage", PermissionScope.COMPANY),
            entry("contract:read", PermissionScope.COMPANY),
            entry("contract:manage", PermissionScope.COMPANY),
            entry("leave:read", PermissionScope.COMPANY),
            entry("leave:approve", PermissionScope.COMPANY),
            entry("holiday:manage", PermissionScope.ALL),
            entry("payroll:read", PermissionScope.COMPANY),
            entry("payroll:run", PermissionScope.COMPANY),
            entry("payslip:read", PermissionScope.COMPANY),
            entry("loan:read", PermissionScope.COMPANY),
            entry("loan:manage", PermissionScope.COMPANY),
            entry("asset:read", PermissionScope.COMPANY),
            entry("asset:manage", PermissionScope.COMPANY),
            entry("bank_batch:read", PermissionScope.COMPANY),
            entry("bank_batch:build", PermissionScope.COMPANY),
            entry("report:generate", PermissionScope.COMPANY),
            entry("data:import", PermissionScope.COMPANY),
            entry("data:export", PermissionScope.COMPANY),
            entry("user:invite", PermissionScope.ALL))),

    /** Ve a su equipo por la cadena de supervisión, pero su nómina es solo suya. */
    SUPERVISOR("Supervisor", "Gestiona a su equipo directo e indirecto", false, Map.ofEntries(
            entry("employee:read", PermissionScope.TEAM),
            entry("leave:read", PermissionScope.TEAM),
            entry("leave:approve", PermissionScope.TEAM),
            entry("leave:request", PermissionScope.SELF),
            entry("asset:read", PermissionScope.TEAM),
            entry("payslip:read", PermissionScope.SELF))),

    /** Autoservicio: todo con alcance SELF. Es el rol del portal del empleado. */
    EMPLOYEE("Empleado", "Acceso de autoservicio a sus propios datos", false, Map.ofEntries(
            entry("payslip:read", PermissionScope.SELF),
            entry("leave:read", PermissionScope.SELF),
            entry("leave:request", PermissionScope.SELF),
            entry("loan:read", PermissionScope.SELF),
            entry("asset:read", PermissionScope.SELF)));

    private final String displayName;
    private final String description;
    private final boolean allPermissions;
    private final Map<String, PermissionScope> grants;

    SystemRole(String displayName, String description, boolean allPermissions,
               Map<String, PermissionScope> grants) {
        this.displayName = displayName;
        this.description = description;
        this.allPermissions = allPermissions;
        this.grants = grants;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** {@code true} si recibe todo el catálogo en lugar de una lista explícita. */
    public boolean isAllPermissions() {
        return allPermissions;
    }

    public Map<String, PermissionScope> grants() {
        return grants;
    }
}
