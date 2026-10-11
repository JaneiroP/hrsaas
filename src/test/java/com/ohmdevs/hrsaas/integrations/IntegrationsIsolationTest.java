package com.ohmdevs.hrsaas.integrations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado de {@code integrations} (regla no negociable: "todo
 * módulo nuevo con datos de tenant necesita su test de aislamiento cruzado").
 *
 * <p>Este módulo tiene una vía que los demás no tienen: una petición sin
 * usuario, que trae el tenant en la URL. Por eso aquí se prueba, además de la
 * API administrativa, que esa vía no sirve para cruzar de un tenant a otro ni
 * siquiera con un secreto legítimo en la mano.
 */
class IntegrationsIsolationTest extends AttendanceWebhookTestSupport {

    private static final String DEVICE = "0d2f6b6e-5a43-4c60-9a57-3b7d4d0a1c22";

    @Test
    @DisplayName("el propietario de un tenant no ve ni puede tocar los conectores de otro")
    void connectorsDoNotLeakThroughTheAdminApi() {
        Site acme = provisionSite("int-iso-a");
        Site globex = provisionSite("int-iso-b");
        String globexConnector = url("/api/v1/integrations/attendance-connectors/" + globex.connector().id());

        ConnectorDto[] seenByAcme = restTemplate.exchange(url("/api/v1/integrations/attendance-connectors"),
                HttpMethod.GET, bearer(acme.token(), null), ConnectorDto[].class).getBody();
        assertThat(List.of(seenByAcme)).extracting(ConnectorDto::id)
                .containsExactly(acme.connector().id());

        assertThat(replaceSecret(acme.token(), globex.connector().id(), newSecret(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(globexConnector, HttpMethod.DELETE, bearer(acme.token(), null),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Y el conector de Globex sigue intacto: acepta su propio secreto de siempre.
        String body = batch(event(globex.employeeId(), DEVICE, "2026-09-05T12:00:00Z", "IN"));
        assertThat(deliver(globex.connector().webhookPath(), globex.secret(), body, BatchDto.class).getBody())
                .isEqualTo(new BatchDto(1, 1, 0, 0));
    }

    @Test
    @DisplayName("un conector no autentica bajo el tenant de otro, aunque la firma sea legitima")
    void aConnectorOnlyExistsUnderItsOwnTenant() {
        Site acme = provisionSite("int-iso-c");
        Site globex = provisionSite("int-iso-d");
        String body = batch(event(acme.employeeId(), DEVICE, "2026-09-06T12:00:00Z", "IN"));
        // El conector y el secreto son de Globex; solo el tenant de la URL es de Acme.
        String globexConnectorUnderAcme =
                "/api/v1/webhooks/attendance/" + acme.tenantId() + "/" + globex.connector().id();

        ResponseEntity<String> response = deliver(globexConnectorUnderAcme, globex.secret(), body, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(timeEntries(acme.tenantId(), acme.employeeId())).isEmpty();
    }

    @Test
    @DisplayName("un conector legitimo no puede escribir marcajes de un empleado de otro tenant")
    void aConnectorCannotRecordPunchesForAnotherTenantsEmployee() {
        Site acme = provisionSite("int-iso-e");
        Site globex = provisionSite("int-iso-f");
        // Globex firma bien, por su propia URL, pero nombra a un empleado de Acme.
        String body = batch(
                event(acme.employeeId(), DEVICE, "2026-09-07T12:00:00Z", "IN"),
                event(globex.employeeId(), DEVICE, "2026-09-07T12:05:00Z", "IN"));

        ResponseEntity<BatchDto> response =
                deliver(globex.connector().webhookPath(), globex.secret(), body, BatchDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new BatchDto(2, 1, 0, 1));
        assertThat(timeEntries(acme.tenantId(), acme.employeeId())).isEmpty();
        assertThat(timeEntries(globex.tenantId(), acme.employeeId())).isEmpty();
        assertThat(timeEntries(globex.tenantId(), globex.employeeId())).hasSize(1);
    }
}
