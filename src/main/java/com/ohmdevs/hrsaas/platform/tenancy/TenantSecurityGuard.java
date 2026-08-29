package com.ohmdevs.hrsaas.platform.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Detecta la trampa documentada en {@code V2__platform_rls.sql}: un rol de
 * conexión superusuario, o con {@code BYPASSRLS}, hace que las políticas de
 * {@code FORCE ROW LEVEL SECURITY} no se apliquen en absoluto — la aplicación
 * seguiría funcionando, las pruebas seguirían en verde, y ninguna fila quedaría
 * realmente aislada entre tenants.
 *
 * <p><strong>No se enchufa como comprobación automática de arranque.</strong>
 * En este mismo repositorio, {@code AbstractPostgresTest} conecta deliberadamente
 * como el superusuario que crea Testcontainers para poder sembrar roles y datos
 * de prueba con libertad — es una decisión documentada, no un descuido. Si esta
 * clase se invocara desde un {@code ApplicationListener} en cada arranque del
 * contexto, cada test que extiende {@code AbstractPostgresTest} (incluida esta
 * misma suite) fallaría en el arranque, por una razón que ya se conoce y se
 * acepta ahí. Enchufarlo a un {@code ApplicationRunner} de verdad para producción
 * — donde {@code spring.datasource.username} debe ser un rol restringido, nunca
 * el superusuario — es una decisión operativa que queda pendiente de que exista
 * ese rol de despliegue; por ahora esta clase solo ofrece la comprobación,
 * lista para invocarse desde donde corresponda cuando se tome esa decisión.
 *
 * <p>Se consulta {@code pg_roles} por nombre en vez de {@code current_user}
 * porque quien llama normalmente conoce el rol configurado en
 * {@code spring.datasource.username} y quiere verificar exactamente ese, no el
 * rol de la conexión JDBC concreta que tenga a mano en ese momento (que podría
 * ser {@code rls_probe} u otro rol de prueba).
 */
public final class TenantSecurityGuard {

    private TenantSecurityGuard() {
    }

    /** Resultado de inspeccionar un rol en {@code pg_roles}. */
    public record RoleSafety(String roleName, boolean superuser, boolean bypassRls) {

        /** {@code true} si RLS se aplicaría realmente a este rol. */
        public boolean isSafeForRls() {
            return !superuser && !bypassRls;
        }
    }

    /**
     * Inspecciona {@code pg_roles} para el rol dado.
     *
     * @throws IllegalStateException si el rol no existe en el clúster conectado
     */
    public static RoleSafety inspect(Connection connection, String roleName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = ?")) {
            statement.setString(1, roleName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "El rol '" + roleName + "' no existe en pg_roles de esta conexión");
                }
                return new RoleSafety(roleName, resultSet.getBoolean(1), resultSet.getBoolean(2));
            }
        }
    }

    /**
     * Exige que el rol dado sea seguro para RLS. Falla con detalle suficiente
     * para diagnosticar sin tener que ir a mirar {@code pg_roles} a mano.
     */
    public static void verifySafeForRls(Connection connection, String roleName) throws SQLException {
        RoleSafety safety = inspect(connection, roleName);
        if (!safety.isSafeForRls()) {
            throw new IllegalStateException(
                    "El rol de conexión '" + roleName + "' es superusuario o tiene BYPASSRLS "
                            + "(rolsuper=" + safety.superuser() + ", rolbypassrls=" + safety.bypassRls() + "). "
                            + "RLS NUNCA se aplica a un rol así, ni con FORCE ROW LEVEL SECURITY: "
                            + "las políticas de V2__platform_rls.sql no protegerían nada bajo esta conexión. "
                            + "spring.datasource.username debe apuntar a un rol normal.");
        }
    }
}
