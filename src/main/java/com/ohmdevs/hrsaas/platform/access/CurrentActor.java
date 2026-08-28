package com.ohmdevs.hrsaas.platform.access;

import java.util.Map;
import java.util.Optional;

/**
 * Quién está operando, en qué tenant, y con qué alcance sobre cada permiso.
 *
 * <p>Los permisos vienen ya consolidados de todos sus roles: si el usuario es
 * supervisor (TEAM sobre {@code employee:read}) y además analista de RRHH
 * (COMPANY sobre lo mismo), aquí llega COMPANY.
 *
 * <p>Fíjate en lo que <em>no</em> tiene: ninguna referencia a empleado. El
 * módulo {@code platform} no sabe qué es un empleado, y no puede saberlo sin
 * invertir la dirección de dependencias. Traducir usuario &rarr; empleado es
 * trabajo del módulo {@code people}, que consume este actor.
 */
public record CurrentActor(
        Long userId,
        Long tenantId,
        Map<String, PermissionScope> permissions) {

    public CurrentActor {
        permissions = Map.copyOf(permissions);
    }

    /** Alcance concedido sobre un permiso, vacío si no lo tiene en absoluto. */
    public Optional<PermissionScope> scopeFor(String permissionCode) {
        return Optional.ofNullable(permissions.get(permissionCode));
    }

    public boolean can(String permissionCode) {
        return permissions.containsKey(permissionCode);
    }

    /**
     * {@code true} si el actor tiene el permiso con alcance suficiente.
     *
     * <p>"Suficiente" es contención, no igualdad: a quien pide TEAM le sirve
     * COMPANY, porque su equipo está dentro de su empresa.
     */
    public boolean canAtLeast(String permissionCode, PermissionScope required) {
        return scopeFor(permissionCode)
                .filter(granted -> granted.includes(required))
                .isPresent();
    }
}
