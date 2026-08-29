package com.ohmdevs.hrsaas.platform.tenancy;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Segunda capa de aislamiento: fija {@code app.tenant_id} en cada transacción
 * que abre esta aplicación, que es lo que leen las políticas de RLS creadas en
 * {@code V2__platform_rls.sql}.
 *
 * <p><strong>Por qué se intercepta {@code setAutoCommit(false)} y no el
 * checkout.</strong> En el momento del checkout ({@code getConnection()}) la
 * conexión que entrega el pool sigue en modo autocommit: un {@code SET LOCAL}
 * (o {@code set_config(..., true)}, su equivalente parametrizable) ejecutado
 * ahí se descartaría de inmediato porque no hay transacción abierta que lo
 * contenga. La transición real a modo transaccional ocurre cuando Spring (vía
 * {@code JpaTransactionManager}/{@code DataSourceTransactionManager}) llama
 * {@code connection.setAutoCommit(false)} al principio de cada
 * {@code @Transactional} — incluida cada consulta de Spring Data JPA, que por
 * defecto ya es transaccional aunque sea de solo lectura. Interceptar esa
 * llamada es interceptar el arranque real de la transacción.
 *
 * <p>Se usa {@code set_config(..., true)} — ámbito de transacción, no de
 * sesión. Esto es lo que hace la garantía autolimpiante: al hacer commit o
 * rollback, PostgreSQL revierte el valor por sí solo, sin que este código
 * tenga que acordarse de limpiarlo. Con un pool de conexiones, una conexión
 * física se reutiliza entre checkouts; fijar el tenant a nivel de sesión
 * (ámbito por defecto de {@code set_config}) dejaría el valor pegado a esa
 * conexión física hasta el próximo checkout que lo reescriba — y cualquier
 * código que tomara esa misma conexión física sin pasar por este wrapper (una
 * fuga de abstracción, pero posible) heredaría el tenant de quien la usó
 * antes. Con ámbito de transacción eso no puede pasar: fuera de una
 * transacción, el valor es siempre el que tenía la sesión antes de empezar
 * (normalmente nada).
 *
 * <p><strong>Límite conocido, no defecto:</strong> una sentencia autocommit
 * suelta, ejecutada fuera de cualquier transacción de Spring, nunca dispara
 * esta lógica y correría sin {@code app.tenant_id} fijado (RLS la bloquearía
 * por completo, fallando cerrado, no abierto). Esto no ocurre en este código
 * base, que siempre pasa por transacciones de Spring Data JPA; si algún día
 * apareciera un caso así, sería una razón para revisar este diseño, no un
 * síntoma silencioso.
 */
public class TenantAwareDataSource extends DelegatingDataSource implements Closeable {

    private static final String SET_TENANT_FOR_TRANSACTION = "SELECT set_config('app.tenant_id', ?, true)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    /**
     * Envuelve la conexión real en un proxy dinámico sobre {@link Connection}
     * en vez de una subclase o un decorador escrito a mano: {@code Connection}
     * es una interfaz con decenas de métodos (metadata, savepoints, tipos
     * SQL...) que no nos interesa tocar uno por uno. El proxy delega todo sin
     * cambios salvo la única llamada que nos importa.
     */
    private static Connection wrap(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new TransactionStartInterceptor(target));
    }

    /**
     * Si el {@link DataSource} delegado es cerrable (como {@code
     * HikariDataSource}), lo cierra. Sin este método, Spring nunca detecta un
     * "destroy method" sobre esta clase envoltorio (que es la clase final del
     * bean tras pasar por el {@code BeanPostProcessor} de
     * {@link TenancyConfiguration}) y el pool de conexiones real nunca se
     * apaga al cerrar el contexto de la aplicación.
     */
    @Override
    public void close() throws IOException {
        DataSource target = getTargetDataSource();
        if (target instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IOException("No se pudo cerrar el DataSource delegado " + target.getClass(), e);
            }
        }
    }

    /**
     * Ejecuta el {@code set_config} con el tenant vigente justo después de que
     * la conexión real pase a modo transaccional.
     */
    private static final class TransactionStartInterceptor implements InvocationHandler {

        private final Connection delegate;

        private TransactionStartInterceptor(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(delegate, args);
                if (isTransitionToManualCommit(method, args)) {
                    applyTenant(delegate);
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private static boolean isTransitionToManualCommit(Method method, Object[] args) {
            return "setAutoCommit".equals(method.getName())
                    && args != null && args.length == 1
                    && Boolean.FALSE.equals(args[0]);
        }

        private static void applyTenant(Connection connection) throws SQLException {
            Long tenantId = TenantContext.current();
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_FOR_TRANSACTION)) {
                statement.setString(1, tenantId == null ? "" : tenantId.toString());
                statement.execute();
            } catch (SQLException e) {
                // Una transacción que arrancó sin lograr fijar su tenant no
                // debe seguir viva: quien la reciba vería RLS aplicando un
                // valor viejo o ninguno. Mejor perder la conexión que dejarla
                // en un estado a medias.
                try {
                    connection.close();
                } catch (SQLException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        }
    }
}
