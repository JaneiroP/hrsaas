package com.ohmdevs.hrsaas.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Piezas del módulo que no son dominio.
 *
 * <p>La seguridad HTTP (JWT propio, decisión abierta §14.1 del blueprint)
 * vive en {@link SecurityConfiguration}, no aquí: se separó para que esta
 * clase siga siendo "lo que no encaja en otro sitio" y no una configuración
 * de seguridad con cosas colgadas.
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
