package com.ohmdevs.hrsaas.shared.audit;

import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike de verificación, no un test de regresión de negocio: contesta los
 * dos puntos delicados que el plan marcó explícitamente como "no asumir,
 * verificar antes de escribir la migración con confianza" (AD-07).
 *
 * <p>Bootstrap de Hibernate puro (sin Spring, sin {@code AbstractPostgresTest}),
 * con {@code hibernate.hbm2ddl.auto=create} contra un Postgres desechable
 * propio — nunca el datasource de la app real, que usa {@code validate} y
 * fallaría hasta que las tablas {@code _aud}/{@code revinfo} existan como
 * migración. La generación de DDL ocurre al construir el
 * {@link SessionFactory}; no hace falta persistir ninguna fila para
 * inspeccionar el esquema resultante.
 *
 * <p><strong>Resultados reales, obtenidos corriendo este test contra
 * {@code hibernate-envers:7.4.5.Final} — dos de los tres no coincidían con
 * la hipótesis inicial del diseño:</strong>
 * <ol>
 *   <li><strong>Punto delicado #1 — ¿Envers audita {@code tenant_id}
 *   automáticamente?</strong> No. El campo {@code @TenantId} heredado de
 *   {@link TenantAwareEntity} (un {@code @MappedSuperclass}) está ausente de
 *   {@code employment_contracts_aud}/{@code salary_history_aud}: Envers lo
 *   trata como parte del contexto de conexión, no como estado persistente
 *   auditable. {@code V10__contracts_envers.sql} no incluye esa columna en
 *   las tablas {@code _aud} (una columna que Envers nunca escribiría sería
 *   peor que no tenerla), y {@code V11__contracts_envers_rls.sql} resuelve
 *   su aislamiento vía {@code JOIN} contra {@code revinfo}.</li>
 *   <li><strong>La clave primaria de {@code revinfo} se llama {@code id},
 *   no {@code rev}</strong> — nombre del campo Java heredado de
 *   {@code RevisionMapping}, sin {@code @Column} que lo renombre. Las tablas
 *   {@code _aud} sí tienen una columna {@code rev}: es su FK hacia
 *   {@code revinfo.id}, un nombre de columna distinto al de la tabla padre.</li>
 *   <li><strong>Punto delicado #2 — estrategia de {@code REV}.</strong>
 *   Ninguna de las dos hipótesis originales (secuencia simple o identidad):
 *   {@code revinfo.id} no tiene {@code IDENTITY} ni {@code DEFAULT} a nivel
 *   de PostgreSQL — Hibernate lo genera en la aplicación vía la secuencia
 *   {@code revinfo_seq} (incremento 50, el "hi/lo" por defecto de Hibernate
 *   para {@code @GeneratedValue} sin estrategia explícita) e inserta el
 *   valor ya calculado.</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
class EnversSchemaSpikeTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer SPIKE_POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            SPIKE_POSTGRES.start();
        }
    }

    @Test
    void enversSchemaMatchesWhatV10AndV11Assume() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.url", SPIKE_POSTGRES.getJdbcUrl());
        configuration.setProperty("hibernate.connection.username", SPIKE_POSTGRES.getUsername());
        configuration.setProperty("hibernate.connection.password", SPIKE_POSTGRES.getPassword());
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create");
        configuration.setProperty("hibernate.envers.audit_table_suffix", "_aud");
        configuration.addAnnotatedClass(EmploymentContract.class);
        configuration.addAnnotatedClass(SalaryHistory.class);
        configuration.addAnnotatedClass(TenantRevisionEntity.class);

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            assertThat(sessionFactory.isClosed()).isFalse();
        }

        try (Connection connection = DriverManager.getConnection(
                SPIKE_POSTGRES.getJdbcUrl(), SPIKE_POSTGRES.getUsername(), SPIKE_POSTGRES.getPassword())) {

            // Punto delicado #1: @TenantId NO se audita.
            assertThat(columnsOf(connection, "employment_contracts_aud"))
                    .as("Envers no incluye el campo @TenantId en el snapshot auditado")
                    .doesNotContain("tenant_id");
            assertThat(columnsOf(connection, "salary_history_aud")).doesNotContain("tenant_id");

            // La PK de revinfo se llama "id", no "rev"; sí lleva tenant_id.
            assertThat(columnsOf(connection, "revinfo")).contains("id", "tenant_id", "timestamp");
            assertThat(primaryKeyColumnsOf(connection, "revinfo")).containsExactly("id");

            // Las tablas _aud referencian revinfo por una columna "rev".
            assertThat(columnsOf(connection, "employment_contracts_aud")).contains("rev");
            assertThat(columnsOf(connection, "salary_history_aud")).contains("rev");

            // Punto delicado #2: ni identidad ni secuencia con DEFAULT en la
            // columna — Hibernate calcula e inserta el valor desde la app.
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                         SELECT is_identity, column_default
                         FROM information_schema.columns
                         WHERE table_name = 'revinfo' AND column_name = 'id'
                         """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("is_identity")).isEqualTo("NO");
                assertThat(resultSet.getString("column_default")).isNull();
            }
            assertThat(sequenceNames(connection))
                    .as("Hibernate crea su propia secuencia para generar revinfo.id en la aplicación")
                    .contains("revinfo_seq");
        }
    }

    private static List<String> columnsOf(Connection connection, String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = '" + tableName + "'")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
        }
        return columns;
    }

    private static List<String> primaryKeyColumnsOf(Connection connection, String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT kcu.column_name
                     FROM information_schema.table_constraints tc
                     JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                     WHERE tc.table_name = '%s' AND tc.constraint_type = 'PRIMARY KEY'
                     ORDER BY kcu.ordinal_position
                     """.formatted(tableName))) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
        }
        return columns;
    }

    private static List<String> sequenceNames(Connection connection) throws Exception {
        List<String> sequences = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT sequencename FROM pg_sequences")) {
            while (resultSet.next()) {
                sequences.add(resultSet.getString("sequencename"));
            }
        }
        return sequences;
    }
}
