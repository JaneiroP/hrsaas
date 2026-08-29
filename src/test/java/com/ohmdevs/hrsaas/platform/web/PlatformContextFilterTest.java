package com.ohmdevs.hrsaas.platform.web;

import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Sin contexto de Spring ni Docker: es lógica de un solo filtro, se prueba en
 * milisegundos. Lo que de verdad importa aquí es la limpieza incondicional —
 * con un pool de hilos del servlet container, un contexto sin limpiar se
 * filtra a la siguiente petición que atienda ese mismo hilo.
 */
class PlatformContextFilterTest {

    private final PlatformContextFilter filter = new PlatformContextFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        ActorContext.clear();
    }

    @Test
    @DisplayName("con un CurrentActor autenticado, puebla tenant y actor durante la cadena y limpia después")
    void populatesContextForCurrentActorPrincipal() throws Exception {
        CurrentActor actor = new CurrentActor(7L, 42L, Map.of("employee:read", com.ohmdevs.hrsaas.platform.access.PermissionScope.SELF));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, null));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = (req, res) -> {
            assertThat(TenantContext.current()).isEqualTo(42L);
            assertThat(ActorContext.current()).isEqualTo(actor);
        };

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.current()).isNull();
        assertThat(ActorContext.current()).isNull();
    }

    @Test
    @DisplayName("sin Authentication en el SecurityContext, no puebla nada y deja pasar la petición")
    void noOpWithoutAuthentication() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(TenantContext.current()).isNull();
        assertThat(ActorContext.current()).isNull();
    }

    @Test
    @DisplayName("con un principal que no es CurrentActor, no puebla nada y deja pasar la petición")
    void noOpWithNonCurrentActorPrincipal() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("alguien-mas", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(TenantContext.current()).isNull();
        assertThat(ActorContext.current()).isNull();
    }

    @Test
    @DisplayName("si la cadena lanza, el filtro propaga la excepción y limpia el contexto igual")
    void cleansUpEvenWhenChainThrows() {
        CurrentActor actor = new CurrentActor(7L, 42L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, null));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("fallo en el resto de la cadena");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.current()).isNull();
        assertThat(ActorContext.current()).isNull();
    }
}
