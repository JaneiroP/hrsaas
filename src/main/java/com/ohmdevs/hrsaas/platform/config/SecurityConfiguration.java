package com.ohmdevs.hrsaas.platform.config;

import com.ohmdevs.hrsaas.platform.security.ActorJwtAuthenticationConverter;
import com.ohmdevs.hrsaas.platform.service.ActorLoader;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Cadena de seguridad HTTP: recurso protegido con JWT propio (decisión
 * abierta §14.1 resuelta hacia lo que el blueprint asume, ver
 * {@link com.ohmdevs.hrsaas.platform.security.JwtKeysConfiguration}).
 *
 * <p>Reemplaza el comentario que dejó {@link PlatformConfiguration} ("aquí no
 * se configura la seguridad HTTP, mientras no esté decidido..."): ya se
 * decidió construir el portal del empleado sobre identidad propia, así que
 * dejar todo cerrado por autoconfiguración dejaba de ser el valor por
 * defecto correcto — bloqueaba literalmente cualquier endpoint, incluido el
 * de login.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ActorLoader actorLoader, ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin estado, autenticada por bearer token: no hay cookie de sesión que proteger de CSRF.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ActorJwtAuthenticationConverter(actorLoader)))
                        .authenticationEntryPoint(problemAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(problemAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    /**
     * Sustituye al {@link org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint}
     * por defecto, que responde 401 con cuerpo vacío: aquí todo error, venga
     * de donde venga, es {@code application/problem+json} (blueprint §08).
     *
     * <p>Corre <strong>dentro</strong> de la cadena de filtros de Spring
     * Security, antes de que exista un {@code HandlerMethod} — por eso no
     * puede ser un {@code @ExceptionHandler} de MVC (esos solo atrapan
     * excepciones lanzadas dentro de un controlador) y tiene que escribir la
     * respuesta a mano con el mismo {@link ObjectMapper} (Jackson 3,
     * {@code tools.jackson}) que usa el resto de la aplicación.
     */
    private AuthenticationEntryPoint problemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) ->
                writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "No autenticado",
                        "El token de acceso falta, expiró o es inválido.");
    }

    /** Misma idea que el entry point, para cuando el token es válido pero le falta alcance (hoy sin uso: los endpoints delegan en {@code AccessGuard}, no en {@code hasAuthority}). */
    private AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) ->
                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "Acceso denegado",
                        "No tienes permiso para realizar esta operación.");
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper,
                                      HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("traceId", UUID.randomUUID().toString());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
