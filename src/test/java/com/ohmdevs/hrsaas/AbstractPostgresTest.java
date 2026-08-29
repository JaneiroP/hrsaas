package com.ohmdevs.hrsaas;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests que necesitan base de datos.
 *
 * <p>PostgreSQL real, no H2. RLS, los índices parciales, {@code JSONB} y el
 * comportamiento de las migraciones de Flyway no existen en H2, así que un test
 * verde contra H2 no dice nada sobre lo que va a producción.
 *
 * <p><strong>Patrón "singleton container", nunca {@code @Container}.</strong>
 * Esta clase la extienden varias clases de test distintas ({@code
 * HrsaasApplicationTests}, {@code PlatformIsolationTest}, y cada suite de
 * aislamiento de módulo que se añada después). {@code POSTGRES} es un campo
 * estático declarado <em>aquí</em>, así que es el mismo objeto para todas las
 * subclases, no una copia por clase. Anotarlo con {@code @Container} se probó
 * y rompe la suite completa (aunque cada clase por separado pasa en verde): la
 * extensión de Testcontainers para el contenedor en el {@code afterAll} de la
 * primera clase que termina, pero Spring reutiliza el {@code ApplicationContext}
 * cacheado (misma configuración) para las clases siguientes, que quedan
 * apuntando al puerto de un contenedor ya muerto — el error real fue
 * "Connection to localhost:NNNNN refused" en la segunda clase de la suite.
 * Arrancar el contenedor en un bloque estático y no pararlo nunca de forma
 * explícita es el patrón "singleton container" que documenta el propio
 * Testcontainers: Ryuk lo recoge al terminar la JVM, y el puerto queda estable
 * durante toda la ejecución de {@code mvn test}.
 * Fuente: https://docs.docker.com/guides/testcontainers-java-lifecycle/singleton-containers/
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} se conserva aunque
 * ya no quede ningún campo {@code @Container}: la condición de ejecución que
 * salta los tests sin Docker depende solo de la anotación a nivel de clase, no
 * de que exista un campo anotado (verificado contra el código fuente de
 * {@code TestcontainersExtension}). La comprobación explícita de
 * {@code isDockerAvailable()} en el bloque estático es una defensa adicional:
 * sin ella, una máquina sin Docker haría fallar la carga de la clase con la
 * excepción de arranque del contenedor en vez de saltar los tests con
 * limpieza, que es el comportamiento que este proyecto exige documentar.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgresTest {

    // En Testcontainers 2.x PostgreSQLContainer dejó de ser genérico: ya no
    // lleva el parámetro <SELF> que exigía la 1.x.
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Suppliers: se evalúan cuando arranca el contexto, para entonces el
        // contenedor ya está en pie (el bloque estático de arriba corre antes,
        // al inicializarse esta clase).
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Claves de prueba para AD-08 (cifrado de columna). EncryptedStringConverter
        // es @Component: Spring lo crea en cualquier contexto @SpringBootTest, no
        // solo en tests de people/crypto, así que hace falta un valor aquí para que
        // el resto de la suite no rompa al arrancar. Van por @DynamicPropertySource
        // y no por un application.properties en src/test/resources a propósito: ese
        // archivo, al vivir en test-classes (que Maven pone antes que classes en el
        // classpath de test), REEMPLAZA por completo — no complementa — el
        // application.properties principal, tal como Spring Boot resuelve
        // classpath:/application.properties (toma el primero que encuentra, no los
        // fusiona). Así se perdió "hrsaas.storage.s3.bucket" en una corrida real: no
        // tenía valor por defecto en el @Value y el archivo de test no lo traía.
        // NUNCA usar estas claves fuera de test.
        registry.add("HRSAAS_CRYPTO_DATA_KEY", () -> "mP/JiRLgvFpkfuxEhfKMJCyazEs/E/+rN2QrQYzO284=");
        registry.add("HRSAAS_CRYPTO_HMAC_KEY", () -> "xS6tjDbz2UzZh9IWl2xIfdMCK7M4bpSVFhl/6KMhWsk=");
    }
}
