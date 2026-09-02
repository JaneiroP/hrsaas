package com.ohmdevs.hrsaas.platform.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Emite el access token de sesión. Único punto del código que sabe qué
 * claims lleva el JWT — {@link ActorJwtAuthenticationConverter} es la
 * contraparte que los lee, y ambas clases deben coincidir en los nombres.
 *
 * <p><strong>Deliberadamente no lleva permisos ni alcances como claim.</strong>
 * Un claim es una afirmación del pasado (blueprint §10): meter los permisos
 * en el token los congelaría hasta que expire, y revocar un rol a mitad de
 * sesión no tendría efecto hasta el siguiente login. {@link ActorJwtAuthenticationConverter}
 * los recalcula en cada request contra {@code UserTenant}/{@code UserRole}.
 */
@Component
public class AccessTokenIssuer {

    /** 15 minutos (blueprint §10: "access token corto"). */
    public static final long ACCESS_TOKEN_TTL_SECONDS = 900;

    /**
     * {@code Jwt.getIssuer()} de Spring Security exige que el claim {@code iss}
     * parsee como {@link java.net.URL} (lo comprobó en vivo
     * {@code AccessTokenIssuerTest}: un {@code "hrsaas"} a secas lanza
     * {@code IllegalArgumentException} al leerlo, no al emitirlo — el fallo
     * aparecía en el converter, no aquí). No necesita ser una URL real ni
     * resolver a nada: es un identificador propio, nunca se contacta.
     */
    static final String ISSUER = "https://hrsaas.internal";
    static final String TENANT_CLAIM = "tenant_id";

    private final JwtEncoder jwtEncoder;

    public AccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String issueAccessToken(Long userId, Long tenantId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS))
                .subject(userId.toString())
                .claim(TENANT_CLAIM, tenantId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
