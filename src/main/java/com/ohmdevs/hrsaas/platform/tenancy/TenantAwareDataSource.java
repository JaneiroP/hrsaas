package com.ohmdevs.hrsaas.platform.tenancy;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Segunda capa de aislamiento: fija {@code app.tenant_id} en cada conexión que
 * sale del pool, que es lo que leen las políticas de RLS creadas en
 * {@code V2__platform_rls.sql}.
 *
 * <p>Se usa {@code set_config(..., false)} — ámbito de sesión, no de
 * transacción. La alternativa ({@code SET LOCAL}) solo vive dentro de una
 * transacción y aquí estamos en el momento del checkout, donde puede no haber
 * ninguna abierta.
 *
 * <p>Que el valor sobreviva en la conexión devuelta al pool no filtra nada:
 * cada checkout lo reescribe, y un checkout sin tenant lo deja vacío, con lo
 * que {@code app_current_tenant()} devuelve NULL y las políticas no dejan pasar
 * ninguna fila. Si algún día alguien obtiene una conexión sin pasar por aquí, el
 * resultado es no ver nada — no verlo todo.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SET_TENANT = "SELECT set_config('app.tenant_id', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private Connection applyTenant(Connection connection) throws SQLException {
        Long tenantId = TenantContext.current();
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
            statement.setString(1, tenantId == null ? "" : tenantId.toString());
            statement.execute();
        } catch (SQLException e) {
            // Devolver una conexión sin el ajuste aplicado dejaría RLS operando
            // con el tenant de quien la usó antes. Mejor perderla.
            connection.close();
            throw e;
        }
        return connection;
    }
}
