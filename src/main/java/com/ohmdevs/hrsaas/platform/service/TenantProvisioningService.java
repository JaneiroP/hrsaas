package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.domain.Role;
import com.ohmdevs.hrsaas.platform.domain.Tenant;
import com.ohmdevs.hrsaas.platform.repository.TenantRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Da de alta un tenant: lo crea, siembra sus roles de sistema e invita al
 * propietario.
 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final RoleService roleService;
    private final InvitationService invitationService;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     RoleService roleService,
                                     InvitationService invitationService) {
        this.tenantRepository = tenantRepository;
        this.roleService = roleService;
        this.invitationService = invitationService;
    }

    public record ProvisionedTenant(Tenant tenant, Role ownerRole, String ownerInvitationToken) {
    }

    /**
     * Aprovisiona un tenant nuevo. El tenant queda en {@code PENDING} hasta que
     * el propietario acepte su invitación.
     *
     * <p><strong>Este método no es transaccional, y no puede serlo.</strong> Las
     * tablas del tenant están bajo RLS, y la política compara contra el
     * {@code app.tenant_id} fijado en la conexión al hacer checkout — es decir,
     * antes de que empiece la transacción. Envolver todo en una sola transacción
     * significaría escribir roles con la conexión configurada para "sin tenant",
     * y la política rechazaría cada INSERT.
     *
     * <p>Por eso cada paso corre en su propia transacción, ya dentro del
     * {@link TenantContext} del tenant recién creado. El precio es que el
     * aprovisionamiento no es atómico: si la invitación falla, queda un tenant
     * PENDING con sus roles. Es un precio aceptable — un tenant PENDING no opera,
     * no factura y se puede reintentar por slug o limpiar. La alternativa sería
     * renunciar a RLS, que protege todos los días contra un riesgo bastante peor
     * que un registro huérfano.
     */
    public ProvisionedTenant provision(String slug, String name, String ownerEmail) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Ya existe un tenant con slug '" + slug + "'");
        }

        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(name);
        Tenant saved = tenantRepository.save(tenant);

        return TenantContext.callWith(saved.getId(), () -> {
            Map<SystemRole, Role> roles = roleService.seedSystemRoles();
            Role ownerRole = roles.get(SystemRole.OWNER);
            InvitationService.IssuedInvitation invitation =
                    invitationService.invite(ownerEmail, ownerRole.getId(), null);
            return new ProvisionedTenant(saved, ownerRole, invitation.rawToken());
        });
    }
}
