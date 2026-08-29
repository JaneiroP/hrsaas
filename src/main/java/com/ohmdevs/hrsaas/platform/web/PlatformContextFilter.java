package com.ohmdevs.hrsaas.platform.web;

import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Primera de las tres capas de aislamiento (AD-02): traduce la autenticación ya
 * resuelta por Spring Security en {@link TenantContext}/{@link ActorContext}
 * para el resto de la petición.
 *
 * <p><strong>Por qué corre después del filtro de seguridad, no antes.</strong>
 * Este filtro no autentica nada — solo lee lo que
 * {@link SecurityContextHolder} ya tiene puesto. Debe registrarse con un orden
 * posterior al de la cadena de Spring Security
 * ({@code SecurityFilterProperties.DEFAULT_FILTER_ORDER}) para que la
 * {@link Authentication} ya exista cuando este filtro se ejecute; registrarlo
 * antes lo dejaría siempre en el camino "sin autenticación".
 *
 * <p><strong>Por qué el principal esperado es {@link CurrentActor} y no un
 * contrato nuevo.</strong> El proveedor de autenticación (JWT propio vs.
 * Keycloak, decisión abierta del blueprint §14) todavía no está decidido. Este
 * filtro no elige: si el principal no es un {@code CurrentActor} — por
 * ejemplo, una petición anónima, o un futuro esquema que autentique distinto —
 * simplemente no puebla nada y deja pasar la petición como si el filtro no
 * existiera. Quien decida el proveedor de autenticación solo tiene que
 * garantizar que el principal que deja en el {@code SecurityContext} sea un
 * {@code CurrentActor}; este filtro no cambia.
 *
 * <p><strong>La limpieza en {@code finally} es incondicional.</strong> Con un
 * pool de hilos del servlet container, el hilo que atendió esta petición
 * atenderá otra distinta más tarde. Sin limpiar, la siguiente petición en ese
 * hilo heredaría el tenant/actor de la anterior aunque no tuviera
 * autenticación propia — una fuga silenciosa de aislamiento, no un error
 * visible.
 */
public class PlatformContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
            TenantContext.set(actor.tenantId());
            ActorContext.set(actor);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            ActorContext.clear();
        }
    }
}
