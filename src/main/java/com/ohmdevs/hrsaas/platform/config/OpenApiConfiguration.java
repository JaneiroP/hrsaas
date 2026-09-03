package com.ohmdevs.hrsaas.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documento OpenAPI publicado en {@code /v3/api-docs} (JSON) y
 * {@code /swagger-ui.html} (springdoc, blueprint §08/§12 Fase 5: "OpenAPI
 * generado con springdoc y publicado — es lo que consume el equipo de
 * frontend").
 *
 * <p>springdoc detecta los {@code @RestController} vía reflexión sin
 * anotaciones extra en cada endpoint, pero <strong>no</strong> conoce el
 * esquema de autenticación por sí solo: la auth de este proyecto es JWT
 * propio (decisión abierta §14.1), no el flujo estándar que
 * {@code spring-boot-starter-oauth2-resource-server} expondría con un IdP
 * externo. Declarar el {@link SecurityScheme} aquí es lo que hace aparecer el
 * botón "Authorize" en Swagger UI, para que el equipo de frontend pueda
 * pegar el token de {@code POST /api/v1/auth/login} y probar cualquier
 * endpoint protegido sin salir del navegador.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI hrsaasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("hrsaas API")
                        .version("v1")
                        .description("""
                                SaaS multi-tenant de gestión de personal y nómina para República Dominicana.
                                Autenticación: POST /api/v1/auth/login devuelve un bearer token (JWT propio,
                                RS256, 15 minutos de vigencia) — pegarlo en el botón "Authorize" de esta página
                                para probar cualquier endpoint protegido.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
