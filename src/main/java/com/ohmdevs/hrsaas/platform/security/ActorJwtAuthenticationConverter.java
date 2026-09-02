package com.ohmdevs.hrsaas.platform.security;

import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.service.ActorLoader;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

/**
 * Convierte el {@link Jwt} ya validado (firma y vigencia, verificadas por
 * {@code oauth2ResourceServer().jwt()} antes de que esto se ejecute) en el
 * {@link CurrentActor} que el resto de la aplicación espera como principal.
 *
 * <p><strong>Revalida la pertenencia en cada petición, no confía en el
 * claim.</strong> El {@code tenant_id} del token es una afirmación del
 * pasado (blueprint §10): entre que se emitió y esta petición pueden haber
 * revocado al usuario. Por eso este converter fija {@link TenantContext} el
 * tiempo justo de invocar a {@link ActorLoader#load}, que sí golpea
 * {@code UserTenant}/{@code UserRole} contra la base de datos — y lo limpia
 * de inmediato después, con o sin éxito, para que no quede nada puesto antes
 * de que {@code PlatformContextFilter} (que corre después en la cadena) haga
 * su propio {@code TenantContext.set} a partir del principal ya resuelto.
 *
 * <p>Cualquier fallo (usuario sin pertenencia activa, pertenencia
 * suspendida/revocada) se traduce a {@link InvalidBearerTokenException}: es
 * el tipo que {@code BearerTokenAuthenticationFilter} espera para responder
 * 401 en vez de dejar escapar un 500.
 */
public class ActorJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ActorLoader actorLoader;

    public ActorJwtAuthenticationConverter(ActorLoader actorLoader) {
        this.actorLoader = actorLoader;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt);
        Long tenantId = parseTenantId(jwt);

        CurrentActor actor;
        try {
            actor = TenantContext.callWith(tenantId, () -> actorLoader.load(userId));
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Token no vigente: la pertenencia al tenant ya no es válida", e);
        }
        return new UsernamePasswordAuthenticationToken(actor, jwt, List.of());
    }

    private static Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Token con 'sub' inválido");
        }
    }

    /** Mismo paquete que {@link AccessTokenIssuer}: el claim ({@code package-private}) se lee directo, sin duplicar el literal. */
    private static Long parseTenantId(Jwt jwt) {
        Object claim = jwt.getClaim(AccessTokenIssuer.TENANT_CLAIM);
        if (!(claim instanceof Number number)) {
            throw new InvalidBearerTokenException("Token sin claim 'tenant_id'");
        }
        return number.longValue();
    }
}
