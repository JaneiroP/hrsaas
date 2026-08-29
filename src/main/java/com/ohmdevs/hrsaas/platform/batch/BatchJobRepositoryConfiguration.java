package com.ohmdevs.hrsaas.platform.batch;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * {@code JobRepository} de Spring Batch sobre un datasource H2 en memoria
 * dedicado, separado por completo del datasource de PostgreSQL con RLS.
 *
 * <p><strong>Por qué existe esta clase en vez de dejar que Boot autoconfigure
 * el repositorio.</strong> Verificado contra el jar real (no contra el índice
 * de búsqueda de Maven Central, que dio un falso negativo aquí):
 * {@code spring-boot-starter-batch:4.1.1} no trae ningún wiring propio de
 * {@code JobRepository} sobre JDBC — no existe {@code @BatchDataSource} en
 * este classpath, aparece recién en el milestone 4.2.0-M1 de Boot. Sin
 * datasource propio, la base sin backing store concreto de Spring Batch cae
 * en {@code ResourcelessJobRepository} (en memoria, sin tablas) — que la
 * documentación oficial de Spring Batch marca explícitamente como
 * incompatible con steps particionados o concurrentes, justo lo que
 * {@code TenantPropagationBatchTest} necesita ejercitar.
 * Fuente: https://docs.spring.io/spring-batch/reference/job/configuring-repository.html
 *
 * <p><strong>Trampa de versión real, encontrada verificando en vivo en vez de
 * asumiendo:</strong> {@code spring-boot-starter-batch:4.1.1} resuelve Spring
 * Batch <strong>6.0.5</strong>, no la serie 5.2.x. Batch 6 repaquetizó buena
 * parte del núcleo ({@code Job}/{@code JobExecution}/{@code Step} pasaron a
 * subpaquetes propios) y, más relevante aquí, dividió
 * {@code DefaultBatchConfiguration} en una base sin backing store concreto
 * más {@link JdbcDefaultBatchConfiguration} (JDBC),
 * {@code MongoDefaultBatchConfiguration}, etc. {@code getDataSource()}/
 * {@code getTransactionManager()} viven en la subclase JDBC, no en la base —
 * de ahí que esta clase extienda {@link JdbcDefaultBatchConfiguration}. Es el
 * mismo punto de extensión que buscaba el diseño original, solo que un nivel
 * más abajo en la jerarquía de lo que se había asumido sin verificar.
 *
 * <p><strong>Por qué no hace falta excluir nada del {@code BeanPostProcessor}
 * de {@code TenancyConfiguration}.</strong> Ese post-processor envuelve
 * cualquier <em>bean</em> {@link DataSource} en {@code TenantAwareDataSource}.
 * El datasource H2 de esta clase nunca se declara como {@code @Bean}: se
 * construye una sola vez dentro de {@link #getDataSource()} y se memoiza en un
 * campo. Sin bean, el post-processor nunca lo ve — no hace falta excluirlo
 * por nombre, a diferencia del diseño original que asumía un {@code @Bean}
 * H2 con {@code @BatchDataSource}.
 *
 * <p><strong>Decisión temporal para el smoke test de Fase 0</strong>, a
 * revisar en Fase 3 cuando el motor de nómina necesite jobs reales: si para
 * entonces Boot ya soporta un datasource de Batch por JDBC de forma nativa
 * (4.2 estable), esta clase se reemplaza por esa autoconfiguración.
 */
@Configuration(proxyBeanMethods = false)
public class BatchJobRepositoryConfiguration extends JdbcDefaultBatchConfiguration {

    private static final String JDBC_URL = "jdbc:h2:mem:hrsaas-batch;DB_CLOSE_DELAY=-1";

    private DataSource dataSource;
    private JdbcTransactionManager transactionManager;

    @Override
    protected DataSource getDataSource() {
        if (dataSource == null) {
            dataSource = createAndInitialize();
        }
        return dataSource;
    }

    @Override
    protected PlatformTransactionManager getTransactionManager() {
        return batchTransactionManager();
    }

    /**
     * Expuesto con nombre propio porque el contexto ya tiene otro
     * {@link PlatformTransactionManager} (el de JPA sobre Postgres) — sin un
     * nombre explícito, cualquier inyección por tipo sería ambigua. Lo usan
     * los steps que escriben directamente contra la contabilidad de Batch
     * (como el tasklet de humo de {@code TenantPropagationBatchTest}); un
     * step de negocio real seguiría usando el transaction manager principal
     * para su propio trabajo.
     *
     * <p><strong>{@code defaultCandidate = false} no es cosmético — sin él la
     * app no arranca.</strong> La autoconfiguración de JPA registra su propio
     * {@code transactionManager} bajo
     * {@code @ConditionalOnMissingBean(TransactionManager.class)}, que
     * empareja <em>por tipo</em>, no por nombre. Sin esta bandera, este bean
     * (también un {@link PlatformTransactionManager}) satisface esa condición
     * primero y Boot se retira sin crear el {@code transactionManager} de
     * JPA — cualquier {@code @Transactional} de la aplicación revienta con
     * {@code NoSuchBeanDefinitionException: No bean named 'transactionManager'}.
     * Encontrado corriendo la suite completa, no en revisión de código.
     * {@code defaultCandidate = false} saca este bean de esa resolución por
     * tipo sin dejar de poder inyectarlo por nombre/{@code @Qualifier}, que es
     * como lo consume el tasklet de humo.
     */
    @Bean(name = "batchTransactionManager", defaultCandidate = false)
    public JdbcTransactionManager batchTransactionManager() {
        if (transactionManager == null) {
            transactionManager = new JdbcTransactionManager(getDataSource());
        }
        return transactionManager;
    }

    private static DataSource createAndInitialize() {
        DriverManagerDataSource h2 = new DriverManagerDataSource();
        h2.setDriverClassName("org.h2.Driver");
        h2.setUrl(JDBC_URL);
        h2.setUsername("sa");
        h2.setPassword("");

        // Esquema oficial de Spring Batch, tal cual lo trae spring-batch-core
        // (org/springframework/batch/core/schema-h2.sql) — no se copia a mano
        // para no divergir de la versión real de la librería.
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
        // 'continueOnError': con el patrón "singleton container" que ya usa
        // AbstractPostgresTest, varias clases de test comparten el mismo
        // ApplicationContext cacheado y por tanto esta misma instancia de
        // datasource — pero si algún día un contexto distinto reutilizara el
        // mismo nombre de base H2 en memoria dentro de la misma JVM, un
        // segundo intento de CREATE TABLE no debe tumbar el arranque.
        populator.setContinueOnError(true);
        populator.execute(h2);
        return h2;
    }
}
