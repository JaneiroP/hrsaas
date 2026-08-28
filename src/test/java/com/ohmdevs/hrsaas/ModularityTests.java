package com.ohmdevs.hrsaas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Las fronteras entre módulos son parte del build (AD-01).
 *
 * <p>Verifica que ningún módulo alcance el interior de otro y que no haya ciclos.
 * No necesita base de datos: es análisis estático del bytecode.
 *
 * <p>Este test es lo que mantiene honesto al monolito modular. Sin él, las
 * fronteras son un acuerdo entre caballeros que dura hasta el primer viernes
 * con prisa.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(HrsaasApplication.class);

    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }

    @Test
    void printsModuleStructure() {
        System.out.println(MODULES);
    }
}
