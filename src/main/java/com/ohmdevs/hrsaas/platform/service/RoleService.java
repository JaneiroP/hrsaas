package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.platform.domain.Permission;
import com.ohmdevs.hrsaas.platform.domain.Role;
import com.ohmdevs.hrsaas.platform.domain.RolePermission;
import com.ohmdevs.hrsaas.platform.repository.PermissionRepository;
import com.ohmdevs.hrsaas.platform.repository.RolePermissionRepository;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Roles y concesiones de permiso del tenant activo.
 *
 * <p>Todos los métodos exigen {@link TenantContext} establecido: {@code @TenantId}
 * rellena {@code tenant_id} al insertar y RLS lo verifica del lado de PostgreSQL.
 * Sin contexto, la escritura no falla silenciosamente — falla.
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /** Crea los roles de {@link SystemRole} con sus concesiones. Idempotente por nombre. */
    @Transactional
    public Map<SystemRole, Role> seedSystemRoles() {
        TenantContext.require();
        List<Permission> catalog = permissionRepository.findAll();
        Map<SystemRole, Role> created = new EnumMap<>(SystemRole.class);

        for (SystemRole definition : SystemRole.values()) {
            Role role = roleRepository.findByName(definition.name()).orElseGet(() -> {
                Role fresh = new Role();
                fresh.setName(definition.name());
                fresh.setDescription(definition.description());
                fresh.setSystemRole(true);
                return roleRepository.save(fresh);
            });
            created.put(definition, role);

            if (definition.isAllPermissions()) {
                catalog.forEach(permission -> grantCapped(role, permission, PermissionScope.ALL));
            } else {
                definition.grants().forEach((code, desired) -> {
                    Permission permission = permissionRepository.findByCode(code).orElseThrow(
                            () -> new IllegalStateException(
                                    "El rol de sistema %s concede '%s', que no está en el catálogo. "
                                            .formatted(definition, code)
                                            + "Falta una migración del catálogo de permisos."));
                    grantCapped(role, permission, desired);
                });
            }
        }
        return created;
    }

    /**
     * Concede un permiso a un rol con un alcance explícito.
     *
     * <p>Rechaza pedir más de lo que el permiso admite en vez de recortarlo en
     * silencio: quien llama a esta API cree que está concediendo COMPANY, y
     * darle SELF sin avisar produce un bug que se descubre en producción.
     */
    @Transactional
    public RolePermission grant(Long roleId, String permissionCode, PermissionScope scope) {
        Role role = roleRepository.findById(roleId).orElseThrow(
                () -> new IllegalArgumentException("Rol " + roleId + " no existe en este tenant"));
        Permission permission = permissionRepository.findByCode(permissionCode).orElseThrow(
                () -> new IllegalArgumentException("Permiso '" + permissionCode + "' no existe"));

        if (!permission.getMaxScope().includes(scope)) {
            throw new IllegalArgumentException(
                    "El permiso '%s' admite como máximo alcance %s; se pidió %s"
                            .formatted(permissionCode, permission.getMaxScope(), scope));
        }
        return upsert(role, permission, scope);
    }

    @Transactional
    public void revoke(Long roleId, String permissionCode) {
        Permission permission = permissionRepository.findByCode(permissionCode).orElseThrow(
                () -> new IllegalArgumentException("Permiso '" + permissionCode + "' no existe"));
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permission.getId());
    }

    @Transactional
    public Role createRole(String name, String description) {
        TenantContext.require();
        roleRepository.findByName(name).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe un rol '" + name + "' en este tenant");
        });
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    /** Recorta al techo del permiso. Solo para el sembrado, donde pedir "todo" es la intención. */
    private void grantCapped(Role role, Permission permission, PermissionScope desired) {
        PermissionScope effective = desired.includes(permission.getMaxScope())
                ? permission.getMaxScope()
                : desired;
        upsert(role, permission, effective);
    }

    private RolePermission upsert(Role role, Permission permission, PermissionScope scope) {
        RolePermission grant = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .filter(existing -> existing.getPermission().getId().equals(permission.getId()))
                .findFirst()
                .orElseGet(() -> {
                    RolePermission fresh = new RolePermission();
                    fresh.setRole(role);
                    fresh.setPermission(permission);
                    return fresh;
                });
        grant.setScope(scope);
        return rolePermissionRepository.save(grant);
    }
}
