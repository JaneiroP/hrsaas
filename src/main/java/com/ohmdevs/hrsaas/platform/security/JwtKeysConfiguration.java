package com.ohmdevs.hrsaas.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Claves del emisor propio de JWT (decisión abierta §14.1 del blueprint: se
 * implementa lo que el plan asume, identidad dentro de {@code platform}, sin
 * IdP externo).
 *
 * <p><strong>Limitación deliberada de esta pasada, para dejarla explícita en
 * vez de que se descubra en producción:</strong> el par RSA se genera una vez
 * en memoria al arrancar la aplicación, no se persiste ni rota vía JWKS
 * (blueprint §10: "claves rotables vía JWKS"). Sirve para una sola instancia:
 * cualquier réplica adicional del proceso tendría su propia clave y no
 * validaría los tokens emitidos por otra, y un reinicio invalida todas las
 * sesiones activas. Antes de un despliegue con más de una instancia hace
 * falta mover esto a un par de claves persistido (KMS o secreto montado) y
 * publicar {@code /.well-known/jwks.json} — ninguna de las dos cosas está
 * hecha todavía.
 */
@Configuration(proxyBeanMethods = false)
public class JwtKeysConfiguration {

    @Bean
    RSAKey rsaJsonWebKey() {
        KeyPair keyPair = generateRsaKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaJsonWebKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaJsonWebKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaJsonWebKey) throws com.nimbusds.jose.JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaJsonWebKey.toRSAPublicKey()).build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA no disponible en esta JVM", e);
        }
    }
}
