package com.ohmdevs.hrsaas.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Piezas del módulo que no son dominio.
 *
 * <p>Aquí <em>no</em> se configura la seguridad HTTP: mientras no esté decidido
 * si la identidad la emite la propia aplicación o un proveedor externo, la
 * autoconfiguración de Spring Security deja todo cerrado, que es el valor por
 * defecto correcto. Los endpoints llegan con esa decisión, no antes.
 */
@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {

    /**
     * Codificador delegante: escribe con BCrypt y prefija el algoritmo
     * ({@code {bcrypt}$2a$...}).
     *
     * <p>El prefijo es lo que permite migrar a Argon2 dentro de unos años sin
     * invalidar las contraseñas existentes: convivirán los dos formatos y cada
     * hash dirá cómo verificarse.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
