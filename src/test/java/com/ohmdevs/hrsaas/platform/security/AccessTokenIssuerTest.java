package com.ohmdevs.hrsaas.platform.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el ciclo completo emisor→decodificador sin levantar Spring ni base
 * de datos: instancia {@link JwtKeysConfiguration} directamente como una
 * fábrica de objetos normal (sus métodos {@code @Bean} son solo métodos).
 *
 * <p>Es la única forma de verificar en esta sesión que
 * {@code NimbusJwtEncoder} + {@link RSAKey} realmente producen un JWT que
 * {@code NimbusJwtDecoder} valida y del que se recuperan los mismos claims
 * ({@code sub}, {@code tenant_id}) que lee {@link ActorJwtAuthenticationConverter}
 * — todo lo que depende de Postgres (login real, resource server de extremo a
 * extremo) queda sin ejecutar aquí por falta de Docker en este entorno, y
 * este test es la red de seguridad mínima para esa pieza mientras tanto.
 */
class AccessTokenIssuerTest {

    private final JwtKeysConfiguration keysConfiguration = new JwtKeysConfiguration();
    private final RSAKey rsaKey = keysConfiguration.rsaJsonWebKey();
    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    private final AccessTokenIssuer issuer = new AccessTokenIssuer(jwtEncoder);

    @Test
    void issuedTokenDecodesWithMatchingClaims() throws Exception {
        String token = issuer.issueAccessToken(42L, 7L);
        assertThat(token).isNotBlank();

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(((Number) decoded.getClaim("tenant_id")).longValue()).isEqualTo(7L);
        assertThat(decoded.getIssuer().toString()).contains("hrsaas");
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
        assertThat(decoded.getExpiresAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(AccessTokenIssuer.ACCESS_TOKEN_TTL_SECONDS + 5));
    }

    @Test
    void tokenSignedByADifferentKeyIsRejected() throws Exception {
        String token = issuer.issueAccessToken(1L, 1L);

        RSAKey otherKey = keysConfiguration.rsaJsonWebKey();
        JwtDecoder decoderWithWrongKey = NimbusJwtDecoder.withPublicKey(otherKey.toRSAPublicKey()).build();

        assertThatThrownBy(() -> decoderWithWrongKey.decode(token))
                .as("un decoder con una clave publica distinta a la que firmo el token debe rechazarlo")
                .isInstanceOf(Exception.class);
    }
}
