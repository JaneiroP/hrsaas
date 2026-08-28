package com.ohmdevs.hrsaas;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests que necesitan base de datos.
 *
 * <p>PostgreSQL real, no H2. RLS, los índices parciales, {@code JSONB} y el
 * comportamiento de las migraciones de Flyway no existen en H2, así que un test
 * verde contra H2 no dice nada sobre lo que va a producción.
 *
 * <p>{@code disabledWithoutDocker} hace que la suite se salte estos tests en una
 * máquina sin Docker en lugar de fallar. En CI, Docker está, y entonces corren.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgresTest {

    // En Testcontainers 2.x PostgreSQLContainer dejó de ser genérico: ya no
    // lleva el parámetro <SELF> que exigía la 1.x.
    @Container
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Suppliers: se evalúan cuando arranca el contexto, para entonces el
        // contenedor ya está en pie.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
