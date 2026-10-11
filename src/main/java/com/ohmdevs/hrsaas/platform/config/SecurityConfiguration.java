package com.ohmdevs.hrsaas.platform.config;

import com.ohmdevs.hrsaas.platform.security.ActorJwtAuthenticationConverter;
import com.ohmdevs.hrsaas.platform.service.ActorLoader;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
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
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ActorLoader actorLoader, ObjectMapper objectMapper,
                                                CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin estado, autenticada por bearer token: no hay cookie de sesión que proteger de CSRF.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Documento OpenAPI y Swagger UI (springdoc): público a
                        // propósito, igual que el login — es lo que el equipo de
                        // frontend consulta antes de tener credenciales propias.
                        // No expone datos de negocio, solo la forma de la API.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Webhooks entrantes: los llama otro sistema, que no tiene
                        // usuario ni token. No quedan abiertos: cada petición se
                        // autentica por su firma HMAC dentro del controlador
                        // (ver integrations.web.AttendanceWebhookController).
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ActorJwtAuthenticationConverter(actorLoader)))
                        .authenticationEntryPoint(problemAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(problemAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    /**
     * Habilita al frontend (Angular, otro origen/puerto en desarrollo —
     * {@code ng serve} en 4200 contra este backend en 8080) a llamar la API
     * desde el navegador. Sin esto, el token del bearer llega bien por
     * Postman/curl pero el navegador bloquea la respuesta antes de que el
     * código JavaScript la vea: el mismo backend "funciona" en una prueba
     * manual y falla en la app real, una discrepancia que cuesta tiempo
     * diagnosticar si no se sabe que CORS nunca se configuró.
     *
     * <p>{@code allowCredentials(true)} + orígenes explícitos (nunca
     * {@code "*"}) porque el navegador rechaza la combinación de comodín con
     * credenciales — y aquí "credenciales" incluye la cabecera
     * {@code Authorization} con el bearer token, no solo cookies. La lista de
     * orígenes es configurable por entorno ({@code hrsaas.cors.allowed-origins},
     * coma-separado) porque el dominio real de producción del frontend no se
     * conoce todavía — pendiente de ajustar cuando exista.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${hrsaas.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
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
