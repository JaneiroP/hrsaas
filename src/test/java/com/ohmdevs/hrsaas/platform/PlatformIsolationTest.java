package com.ohmdevs.hrsaas.platform;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.platform.domain.Role;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.service.ActorLoader;
import com.ohmdevs.hrsaas.platform.service.InvitationNotRedeemableException;
import com.ohmdevs.hrsaas.platform.service.InvitationService;
import com.ohmdevs.hrsaas.platform.service.RoleService;
import com.ohmdevs.hrsaas.platform.service.SystemRole;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aislamiento entre tenants y alcance de permisos, contra PostgreSQL real.
 *
 * <p>Éste es el test del que depende el argumento de venta del producto: "los
 * datos de tu empresa no se mezclan con los de otra". Cubre las dos capas por
 * separado, porque protegen de cosas distintas.
 *
 * <p><strong>La clase no es {@code @Transactional} a propósito.</strong> Un
 * rollback automático dejaría los datos invisibles para la conexión JDBC cruda
 * con la que se comprueba RLS, y ese chequeo es justamente el que no se puede
 * falsear.
 */
class PlatformIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private RoleService roles;
    @Autowired
    private ActorLoader actorLoader;
    @Autowired
    private RoleRepository roleRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;

    @BeforeEach
    void provisionTwoTenants() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");
    }

    @Test
    @DisplayName("un rol creado en un tenant no existe para el otro")
    void rolesDoNotLeakBetweenTenants() {
        TenantContext.runWith(acme.tenant().getId(),
                () -> roles.createRole("AUDITOR_EXTERNO", "Solo existe en Acme"));

        List<String> acmeRoles = TenantContext.callWith(acme.tenant().getId(),
                () -> roleRepository.findAll().stream().map(Role::getName).toList());
        List<String> globexRoles = TenantContext.callWith(globex.tenant().getId(),
                () -> roleRepository.findAll().stream().map(Role::getName).toList());

        assertThat(acmeRoles).contains("AUDITOR_EXTERNO");
        assertThat(globexRoles).doesNotContain("AUDITOR_EXTERNO");

        // Y no es que solo falte por nombre: para Globex ese rol no se resuelve.
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> roleRepository.findByName("AUDITOR_EXTERNO"))).isEmpty();
    }

    @Test
    @DisplayName("los roles de sistema de cada tenant son filas distintas")
    void systemRolesAreSeededPerTenant() {
        Long acmeOwnerId = TenantContext.callWith(acme.tenant().getId(),
                () -> roleRepository.findByName(SystemRole.OWNER.name()).orElseThrow().getId());
        Long globexOwnerId = TenantContext.callWith(globex.tenant().getId(),
                () -> roleRepository.findByName(SystemRole.OWNER.name()).orElseThrow().getId());

        assertThat(acmeOwnerId).isNotEqualTo(globexOwnerId);
    }

    @Test
    @DisplayName("el propietario acepta su invitación y recibe permisos acotados por max_scope")
    void ownerAcceptsInvitationAndIsCappedByMaxScope() {
        Long tenantId = acme.tenant().getId();
        var owner = TenantContext.callWith(tenantId,
                () -> invitations.accept(acme.ownerInvitationToken(), "Dueña de Acme", "un-secreto-largo"));

        CurrentActor actor = TenantContext.callWith(tenantId, () -> actorLoader.load(owner.getId()));

        // Administrar el tenant no tiene techo: llega a ALL.
        assertThat(actor.scopeFor("tenant:manage")).contains(PermissionScope.ALL);

        // Aprobar nómina sí lo tiene. Aunque OWNER pide "todo", el catálogo
        // limita este permiso a COMPANY y el sembrado lo recorta.
        assertThat(actor.scopeFor("payroll:approve")).contains(PermissionScope.COMPANY);
        assertThat(actor.canAtLeast("payroll:approve", PermissionScope.COMPANY)).isTrue();
    }

    @Test
    @DisplayName("el rol de empleado solo alcanza sus propios datos")
    void employeeRoleIsSelfScoped() {
        Long tenantId = acme.tenant().getId();

        Long employeeRoleId = TenantContext.callWith(tenantId,
                () -> roleRepository.findByName(SystemRole.EMPLOYEE.name()).orElseThrow().getId());
        var issued = TenantContext.callWith(tenantId,
                () -> invitations.invite("empleado@acme.test", employeeRoleId, null));
        var employee = TenantContext.callWith(tenantId,
                () -> invitations.accept(issued.rawToken(), "Empleado Uno", "otro-secreto-largo"));

        CurrentActor actor = TenantContext.callWith(tenantId, () -> actorLoader.load(employee.getId()));

        assertThat(actor.scopeFor("payslip:read")).contains(PermissionScope.SELF);
        assertThat(actor.canAtLeast("payslip:read", PermissionScope.TEAM)).isFalse();
        assertThat(actor.can("employee:read")).isFalse();
        assertThat(actor.can("payroll:run")).isFalse();
    }

    @Test
    @DisplayName("una invitación no se puede canjear dos veces")
    void invitationIsSingleUse() {
        Long tenantId = globex.tenant().getId();
        String token = globex.ownerInvitationToken();

        TenantContext.runWith(tenantId, () -> invitations.accept(token, "Dueño de Globex", "secreto-largo-1"));

        assertThatThrownBy(() ->
                TenantContext.runWith(tenantId, () -> invitations.accept(token, "Impostor", "secreto-largo-2")))
                .isInstanceOf(InvitationNotRedeemableException.class);
    }

    @Test
    @DisplayName("RLS no deja pasar una fila si la conexión no declara tenant")
    void rowLevelSecurityBlocksConnectionsWithoutTenant() throws Exception {
        // La aplicación se conecta en estos tests como superusuario, y RLS NUNCA
        // aplica a un superusuario, ni con FORCE. Si probáramos con esa conexión
        // el test daría verde sin ejercitar una sola política — que es justo la
        // trampa documentada en V2__platform_rls.sql. Por eso: rol normal.
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rls_probe') THEN
                            CREATE ROLE rls_probe LOGIN PASSWORD 'probe';
                        END IF;
                    END $$
                    """);
            statement.execute("GRANT USAGE ON SCHEMA public TO rls_probe");
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO rls_probe");
        }

        try (Connection probe = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "rls_probe", "probe")) {
            assertThat(countRoles(probe))
                    .as("sin app.tenant_id la política no deja pasar ninguna fila")
                    .isZero();

            try (Statement statement = probe.createStatement()) {
                statement.execute("SELECT set_config('app.tenant_id', '" + acme.tenant().getId() + "', false)");
            }
            assertThat(countRoles(probe))
                    .as("con el tenant fijado se ven sus roles")
                    .isPositive();
        }
    }

    private static long countRoles(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM roles")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
