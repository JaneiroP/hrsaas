package com.ohmdevs.hrsaas.platform;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.platform.domain.InvitationStatus;
import com.ohmdevs.hrsaas.platform.domain.Role;
import com.ohmdevs.hrsaas.platform.domain.User;
import com.ohmdevs.hrsaas.platform.repository.InvitationRepository;
import com.ohmdevs.hrsaas.platform.repository.RolePermissionRepository;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.repository.UserRoleRepository;
import com.ohmdevs.hrsaas.platform.repository.UserTenantRepository;
import com.ohmdevs.hrsaas.platform.service.ActorLoader;
import com.ohmdevs.hrsaas.platform.service.InvitationNotRedeemableException;
import com.ohmdevs.hrsaas.platform.service.InvitationService;
import com.ohmdevs.hrsaas.platform.service.RoleService;
import com.ohmdevs.hrsaas.platform.service.SystemRole;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.platform.tenancy.TenantSecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private UserTenantRepository userTenantRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;

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
    @DisplayName("una invitación pendiente de un tenant no se resuelve consultando desde el otro")
    void invitationsDoNotLeakBetweenTenants() {
        Long acmeTenantId = acme.tenant().getId();
        Long globexTenantId = globex.tenant().getId();

        Long employeeRoleId = TenantContext.callWith(acmeTenantId,
                () -> roleRepository.findByName(SystemRole.EMPLOYEE.name()).orElseThrow().getId());
        InvitationService.IssuedInvitation issued = TenantContext.callWith(acmeTenantId,
                () -> invitations.invite("empleado@acme.test", employeeRoleId, null));
        String tokenHash = issued.invitation().getTokenHash();
        String email = issued.invitation().getEmail();

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> invitationRepository.findByTokenHash(tokenHash)))
                .as("bajo Acme, la invitación que acaba de emitir se resuelve por su hash")
                .isPresent();
        assertThat(TenantContext.callWith(globexTenantId,
                () -> invitationRepository.findByTokenHash(tokenHash)))
                .as("el mismo hash consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING)))
                .as("bajo Acme aparece la pendiente que acaba de emitir")
                .hasSize(1);
        assertThat(TenantContext.callWith(globexTenantId,
                () -> invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING)))
                .as("el mismo correo consultado bajo Globex no trae la invitación de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("la asignación de un rol a un usuario no se ve consultando desde otro tenant")
    void userRoleAssignmentsDoNotLeakBetweenTenants() {
        Long acmeTenantId = acme.tenant().getId();
        Long globexTenantId = globex.tenant().getId();

        User acmeOwner = TenantContext.callWith(acmeTenantId,
                () -> invitations.accept(acme.ownerInvitationToken(), "Dueña de Acme", "un-secreto-largo"));
        Long ownerRoleId = acme.ownerRole().getId();

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> userRoleRepository.findByUserId(acmeOwner.getId())))
                .as("bajo Acme se ve la asignación recién creada")
                .hasSize(1);
        assertThat(TenantContext.callWith(globexTenantId,
                () -> userRoleRepository.findByUserId(acmeOwner.getId())))
                .as("el mismo user_id consultado bajo Globex no devuelve nada: la fila pertenece a Acme")
                .isEmpty();

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> userRoleRepository.existsByUserIdAndRoleId(acmeOwner.getId(), ownerRoleId)))
                .isTrue();
        assertThat(TenantContext.callWith(globexTenantId,
                () -> userRoleRepository.existsByUserIdAndRoleId(acmeOwner.getId(), ownerRoleId)))
                .isFalse();
    }

    @Test
    @DisplayName("la pertenencia de un usuario a un tenant no es visible consultando desde otro")
    void userTenantMembershipDoesNotLeakBetweenTenants() {
        Long acmeTenantId = acme.tenant().getId();
        Long globexTenantId = globex.tenant().getId();

        User acmeOwner = TenantContext.callWith(acmeTenantId,
                () -> invitations.accept(acme.ownerInvitationToken(), "Dueña de Acme", "un-secreto-largo"));

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> userTenantRepository.findByUserId(acmeOwner.getId())))
                .as("bajo Acme se ve su propia pertenencia")
                .isPresent();
        assertThat(TenantContext.callWith(globexTenantId,
                () -> userTenantRepository.findByUserId(acmeOwner.getId())))
                .as("el mismo user_id consultado bajo Globex no devuelve nada: la fila pertenece a Acme")
                .isEmpty();

        assertThat(TenantContext.callWith(acmeTenantId,
                () -> userTenantRepository.existsByUserId(acmeOwner.getId())))
                .isTrue();
        assertThat(TenantContext.callWith(globexTenantId,
                () -> userTenantRepository.existsByUserId(acmeOwner.getId())))
                .isFalse();
    }

    @Test
    @DisplayName("las concesiones de permiso de un rol no se ven consultando desde otro tenant")
    void rolePermissionGrantsDoNotLeakBetweenTenants() {
        Long acmeOwnerRoleId = acme.ownerRole().getId();

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> rolePermissionRepository.findByRoleId(acmeOwnerRoleId)))
                .as("el rol OWNER de Acme tiene sus concesiones sembradas por RoleService.seedSystemRoles")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> rolePermissionRepository.findByRoleId(acmeOwnerRoleId)))
                .as("el mismo role_id consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("RLS aísla las filas visibles según el tenant fijado en la conexión, no solo por cantidad")
    void rowLevelSecurityIsolatesByActiveTenant() throws Exception {
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ensureProbeRole(admin);
        }

        Long acmeOwnerRoleId = TenantContext.callWith(acme.tenant().getId(),
                () -> roleRepository.findByName(SystemRole.OWNER.name()).orElseThrow().getId());

        try (Connection probe = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "rls_probe", "probe")) {
            assertThat(countRoles(probe))
                    .as("sin app.tenant_id la política no deja pasar ninguna fila")
                    .isZero();

            fixTenant(probe, acme.tenant().getId());
            assertThat(countRoles(probe))
                    .as("con Acme fijado se ven exactamente sus roles de sistema, ni uno más ni uno menos")
                    .isEqualTo(SystemRole.values().length);

            fixTenant(probe, globex.tenant().getId());
            assertThat(countRoles(probe))
                    .as("con Globex fijado se ve su propio conteo de roles de sistema")
                    .isEqualTo(SystemRole.values().length);

            // El conteo por sí solo no descarta que ambos tenants compartan filas
            // por coincidencia. Esto lo hace explícito: la fila concreta del
            // OWNER de Acme es invisible bajo la conexión fijada a Globex.
            try (PreparedStatement statement = probe.prepareStatement(
                    "SELECT count(*) FROM roles WHERE id = ?")) {
                statement.setLong(1, acmeOwnerRoleId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getLong(1))
                            .as("el rol OWNER de Acme no existe bajo la conexión fijada a Globex")
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("TenantSecurityGuard deja pasar al rol de aplicación y atrapa al superusuario de Testcontainers")
    void applicationRuntimeRoleFailsTheSafetyGuard() throws Exception {
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ensureProbeRole(admin);

            assertThatCode(() -> TenantSecurityGuard.verifySafeForRls(admin, "rls_probe"))
                    .as("rls_probe es un rol normal: ni superusuario ni BYPASSRLS, RLS se le aplica de verdad")
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> TenantSecurityGuard.verifySafeForRls(admin, POSTGRES.getUsername()))
                    .as("el superusuario que crea Testcontainers es justo el rol que esta guarda debe atrapar: "
                            + "si la app se conectara con él, RLS no protegería nada")
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /**
     * Crea (si hace falta) el rol de PostgreSQL con el que se prueba RLS de
     * verdad. La aplicación se conecta en estos tests como el superusuario que
     * crea Testcontainers, y RLS nunca aplica a un superusuario, ni con FORCE
     * — probar con esa conexión daría un test verde sin ejercitar una sola
     * política, la trampa documentada en {@code V2__platform_rls.sql}.
     */
    private static void ensureProbeRole(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
    }

    /** Ámbito de sesión a propósito: es la conexión JDBC cruda del probe, no el pool de la app. */
    private static void fixTenant(Connection connection, Long tenantId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
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
