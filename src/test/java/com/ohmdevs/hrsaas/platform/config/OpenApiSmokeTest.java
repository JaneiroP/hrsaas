package com.ohmdevs.hrsaas.platform.config;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El documento OpenAPI y Swagger UI son públicos (blueprint §12 Fase 5) y
 * listan de verdad los endpoints administrativos nuevos — no basta con que
 * springdoc arranque sin error, hay que comprobar que encontró las rutas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OpenApiSmokeTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("el documento OpenAPI se sirve sin autenticación y lista los endpoints administrativos nuevos")
    void openApiDocumentListsAdminEndpoints() {
        ResponseEntity<String> apiDocs = restTemplate.getForEntity(url("/v3/api-docs"), String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = apiDocs.getBody();
        assertThat(body).isNotBlank();
        assertThat(body).contains("/api/v1/employees");
        assertThat(body).contains("/api/v1/contracts");
        assertThat(body).contains("/api/v1/org/companies");
        assertThat(body).contains("bearerAuth");
    }

    @Test
    @DisplayName("Swagger UI se sirve sin autenticación")
    void swaggerUiIsPublic() {
        ResponseEntity<String> swaggerUi = restTemplate.getForEntity(url("/swagger-ui/index.html"), String.class);
        assertThat(swaggerUi.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
