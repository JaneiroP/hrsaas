package com.ohmdevs.hrsaas;

import com.tngtech.archunit.core.domain.JavaClass;
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
 *
 * <p><strong>Una sola excepción, y declarada:</strong> el paquete {@code demo}
 * no se analiza. Es el sembrador de datos del perfil {@code demo}, que por
 * definición usa los servicios internos de casi todos los módulos, igual que
 * los fixtures de {@code src/test} (ver su {@code package-info}). Ningún
 * módulo depende de él; si alguno llegara a importarlo, seguiría fallando
 * aquí, porque lo que se ignora es ese paquete, no quien lo use.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(HrsaasApplication.class,
            JavaClass.Predicates.resideInAPackage("com.ohmdevs.hrsaas.demo.."));

    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }

    @Test
    void printsModuleStructure() {
        System.out.println(MODULES);
    }
}
