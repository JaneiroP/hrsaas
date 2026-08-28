package com.ohmdevs.hrsaas;

import org.junit.jupiter.api.Test;

/**
 * El contexto arranca: Flyway aplica las migraciones, Hibernate valida el
 * esquema contra las entidades y la tenancy se enchufa sin ciclos de beans.
 *
 * <p>Con {@code ddl-auto=validate} este test es más útil de lo que parece: una
 * entidad que no case con su tabla rompe el arranque aquí, no en el despliegue.
 */
class HrsaasApplicationTests extends AbstractPostgresTest {

    @Test
    void contextLoads() {
        // Si el contexto no levanta, el test falla solo.
    }
}
