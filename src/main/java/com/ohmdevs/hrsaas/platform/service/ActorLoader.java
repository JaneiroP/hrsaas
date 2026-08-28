package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.access.GrantedPermission;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.platform.domain.UserTenant;
import com.ohmdevs.hrsaas.platform.repository.UserTenantRepository;
import com.ohmdevs.hrsaas.platform.repository.UserRoleRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el {@link CurrentActor} de una petición: revalida la pertenencia y
 * consolida los permisos de todos sus roles.
 */
@Service
public class ActorLoader {

    private final UserTenantRepository userTenantRepository;
    private final UserRoleRepository userRoleRepository;

    public ActorLoader(UserTenantRepository userTenantRepository, UserRoleRepository userRoleRepository) {
        this.userTenantRepository = userTenantRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Carga el actor para el tenant activo.
     *
     * <p>La pertenencia se comprueba contra la base de datos aunque el token ya
     * afirme el tenant. Un token es una afirmación sobre el pasado: entre su
     * emisión y este request pueden haber revocado al usuario, y quince minutos
     * de acceso indebido a una nómina son quince de más.
     */
    @Transactional(readOnly = true)
    public CurrentActor load(Long userId) {
        Long tenantId = TenantContext.require();

        UserTenant membership = userTenantRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + userId + " no pertenece al tenant " + tenantId));

        if (!membership.getStatus().canAuthenticate()) {
            throw new IllegalStateException(
                    "La pertenencia del usuario " + userId + " está en estado " + membership.getStatus());
        }

        return new CurrentActor(userId, tenantId, consolidate(
                userRoleRepository.findGrantedPermissions(userId)));
    }

    /**
     * Aplasta las concesiones de varios roles a un alcance por permiso.
     *
     * <p>Gana el más amplio. Si alguien es supervisor (TEAM sobre
     * {@code employee:read}) y además analista de RRHH (COMPANY sobre lo mismo),
     * quitarle el rol de supervisor no debe reducirle el acceso que le da el otro.
     */
    private static Map<String, PermissionScope> consolidate(List<GrantedPermission> granted) {
        Map<String, PermissionScope> consolidated = new HashMap<>();
        for (GrantedPermission grant : granted) {
            consolidated.merge(grant.permissionCode(), grant.scope(), PermissionScope::widest);
        }
        return consolidated;
    }
}
