package com.ohmdevs.hrsaas.contracts;

import com.ohmdevs.hrsaas.platform.domain.User;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.service.InvitationService;
import com.ohmdevs.hrsaas.platform.service.SystemRole;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Aprovisiona un tenant con un usuario de rol {@link SystemRole#HR_MANAGER}
 * listo para iniciar sesión contra la API administrativa
 * ({@code org.web}/{@code people.web}/{@code contracts.web}).
 *
 * <p>Vive en {@code src/test}, no en producción — mismo motivo que
 * {@code portal.PortalTestFixture}: el test de fronteras de módulo
 * ({@code ApplicationModules.of(...)}) solo analiza {@code target/classes}.
 */
@Component
public class AdminTestFixture {

    public static final String RAW_PASSWORD = "Cauce#2026-admin";

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private RoleRepository roleRepository;

    public record Provisioned(Long tenantId, Long userId, String email, String password) {
    }

    public Provisioned provisionHrManager(String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        TenantProvisioningService.ProvisionedTenant tenant =
                provisioning.provision(run, "Tenant de prueba " + run, "owner@" + run + ".test");
        return provisionHrManager(tenant.tenant().getId(), slugPrefix);
    }

    /**
     * Añade un {@code HR_MANAGER} a un tenant ya aprovisionado, en vez de crear
     * uno nuevo — hace falta cuando un test necesita, en el mismo tenant, tanto
     * al gerente que arma la nómina como al {@code OWNER} que la aprueba (el
     * maker-checker de {@code payroll:approve}, ver Javadoc de {@link SystemRole#HR_MANAGER}).
     */
    public Provisioned provisionHrManager(Long tenantId, String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return TenantContext.callWith(tenantId, () -> {
            Long hrRoleId = roleRepository.findByName(SystemRole.HR_MANAGER.name()).orElseThrow().getId();
            InvitationService.IssuedInvitation issued = invitations.invite("hr." + run + "@empresa.test", hrRoleId, null);
            User user = invitations.accept(issued.rawToken(), "Gerente de RRHH", RAW_PASSWORD);
            return new Provisioned(tenantId, user.getId(), user.getEmail(), RAW_PASSWORD);
        });
    }

    /**
     * Aprovisiona un tenant nuevo y canjea la invitación de su propio
     * {@code OWNER} ({@link TenantProvisioningService#provision} ya la emite).
     * Sirve para probar {@code payroll:approve}/{@code payroll:reverse}: por
     * diseño (AD maker-checker), {@code HR_MANAGER} no los tiene concedidos —
     * ver Javadoc de {@link SystemRole#HR_MANAGER} — así que solo un
     * {@code OWNER} puede ejercerlos en un test de integración.
     */
    public Provisioned provisionOwner(String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        TenantProvisioningService.ProvisionedTenant tenant =
                provisioning.provision(run, "Tenant de prueba " + run, "owner@" + run + ".test");
        Long tenantId = tenant.tenant().getId();

        return TenantContext.callWith(tenantId, () -> {
            User user = invitations.accept(tenant.ownerInvitationToken(), "Propietario", RAW_PASSWORD);
            return new Provisioned(tenantId, user.getId(), user.getEmail(), RAW_PASSWORD);
        });
    }

    /** Usuario con el rol {@code EMPLOYEE} (alcance {@code SELF} en todo): sirve para probar que la API administrativa le devuelve 403. */
    public Provisioned provisionPlainEmployee(Long tenantId, String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return TenantContext.callWith(tenantId, () -> {
            Long employeeRoleId = roleRepository.findByName(SystemRole.EMPLOYEE.name()).orElseThrow().getId();
            InvitationService.IssuedInvitation issued = invitations.invite("worker." + run + "@empresa.test", employeeRoleId, null);
            User user = invitations.accept(issued.rawToken(), "Empleado Raso", RAW_PASSWORD);
            return new Provisioned(tenantId, user.getId(), user.getEmail(), RAW_PASSWORD);
        });
    }
}
