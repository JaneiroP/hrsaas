package com.ohmdevs.hrsaas.platform.access;

/** Fila cruda de la consulta que resuelve permisos de un usuario, antes de consolidar alcances. */
public record GrantedPermission(String permissionCode, PermissionScope scope) {
}
