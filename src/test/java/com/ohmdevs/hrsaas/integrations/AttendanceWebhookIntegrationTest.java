package com.ohmdevs.hrsaas.integrations;

import com.ohmdevs.hrsaas.contracts.AdminTestFixture;
import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import com.ohmdevs.hrsaas.time.domain.TimeEntrySource;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra HTTP real: un lote firmado como lo envía el sistema
 * de asistencia termina en {@code time_entries}, una sola vez, y nada que no
 * venga bien firmado escribe una fila.
 */
class AttendanceWebhookIntegrationTest extends AttendanceWebhookTestSupport {

    private static final String DEVICE = "0d2f6b6e-5a43-4c60-9a57-3b7d4d0a1c22";

    @Test
    @DisplayName("un lote firmado guarda los marcajes, y reenviarlo no crea ninguno nuevo")
    void recordsABatchOnceAndCountsARetryAsDuplicates() {
        Site site = provisionSite("att-ok");
        String body = batch(
                event(site.employeeId(), DEVICE, "2026-09-01T11:58:00Z", "IN"),
                event(site.employeeId(), DEVICE, "2026-09-01T21:02:00Z", "OUT"));

        ResponseEntity<BatchDto> first = deliver(site.connector().webhookPath(), site.secret(), body, BatchDto.class);
        ResponseEntity<BatchDto> retry = deliver(site.connector().webhookPath(), site.secret(), body, BatchDto.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo(new BatchDto(2, 2, 0, 0));
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).isEqualTo(new BatchDto(2, 0, 2, 0));

        List<TimeEntry> entries = timeEntries(site.tenantId(), site.employeeId());
        assertThat(entries).extracting(TimeEntry::getType)
                .containsExactly(TimeEntryType.CLOCK_IN, TimeEntryType.CLOCK_OUT);
        assertThat(entries).extracting(TimeEntry::getOccurredAt)
                .containsExactly(Instant.parse("2026-09-01T11:58:00Z"), Instant.parse("2026-09-01T21:02:00Z"));
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getSource()).isEqualTo(TimeEntrySource.BIOMETRIC);
            assertThat(entry.getDeviceId()).isEqualTo(DEVICE);
        });

        ConnectorDto afterwards = listConnectors(site.token()).getFirst();
        assertThat(afterwards.lastReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("lo que no se puede autenticar recibe 401 y no escribe nada")
    void rejectsWhatCannotBeAuthenticated() {
        Site site = provisionSite("att-auth");
        String path = site.connector().webhookPath();
        String body = batch(event(site.employeeId(), DEVICE, "2026-09-02T12:00:00Z", "IN"));
        long now = Instant.now().getEpochSecond();
        ConnectorDto withoutSecret = createConnector(site.token(), "Sin secreto todavia");
        String unknownConnector = "/api/v1/webhooks/attendance/" + site.tenantId() + "/" + UUID.randomUUID();

        assertThat(deliver(path, newSecret(), body, String.class).getStatusCode())
                .as("firmado con otro secreto").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(path, signature(site.secret(), now - 3600, body), body, String.class, true).getStatusCode())
                .as("firma de hace una hora").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(path, signature(site.secret(), now, body + " "), body, String.class, true).getStatusCode())
                .as("firma de otro cuerpo").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(path, null, body, String.class, false).getStatusCode())
                .as("sin cabecera de firma").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(unknownConnector, site.secret(), body, String.class).getStatusCode())
                .as("conector inexistente").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(withoutSecret.webhookPath(), site.secret(), body, String.class).getStatusCode())
                .as("conector sin secreto registrado").isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(timeEntries(site.tenantId(), site.employeeId())).isEmpty();
    }

    @Test
    @DisplayName("un evento inutilizable se omite y el resto del lote se guarda")
    void skipsUnusableEventsAndRecordsTheRest() {
        Site site = provisionSite("att-skip");
        String body = batch(
                event(site.employeeId(), DEVICE, "2026-09-03T12:00:00Z", "IN"),
                event(987_654_321L, DEVICE, "2026-09-03T12:01:00Z", "IN"),
                event("EMP-17", DEVICE, "2026-09-03T12:02:00Z", "IN"),
                event(site.employeeId(), DEVICE, "2026-09-03T12:03:00Z", "LUNCH"),
                event(site.employeeId(), DEVICE, "2026-09-03T21:00:00Z", "OUT"));

        ResponseEntity<BatchDto> response = deliver(site.connector().webhookPath(), site.secret(), body, BatchDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new BatchDto(5, 2, 0, 3));
        assertThat(timeEntries(site.tenantId(), site.employeeId())).hasSize(2);
    }

    @Test
    @DisplayName("un cuerpo bien firmado que no es un lote de eventos recibe 400")
    void rejectsAnAuthenticatedBodyThatIsNotABatch() {
        Site site = provisionSite("att-body");
        String path = site.connector().webhookPath();

        assertThat(deliver(path, site.secret(), "esto no es JSON", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deliver(path, site.secret(), "{\"otra\":\"cosa\"}", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deliver(path, site.secret(), batch(), BatchDto.class).getBody())
                .isEqualTo(new BatchDto(0, 0, 0, 0));
    }

    @Test
    @DisplayName("el secreto no sale en ninguna respuesta, y cambiarlo o borrar el conector corta al anterior")
    void theSecretIsWriteOnlyAndReplacingOrDeletingCutsAccess() {
        Site site = provisionSite("att-admin");
        String path = site.connector().webhookPath();
        String body = batch(event(site.employeeId(), DEVICE, "2026-09-04T12:00:00Z", "IN"));
        assertThat(site.connector().hasSigningSecret()).isTrue();
        assertThat(path).isEqualTo("/api/v1/webhooks/attendance/" + site.tenantId() + "/" + site.connector().id());

        String listed = restTemplate.exchange(url("/api/v1/integrations/attendance-connectors"), HttpMethod.GET,
                bearer(site.token(), null), String.class).getBody();
        assertThat(listed).contains(site.connector().id().toString()).doesNotContain(site.secret());

        String rotated = newSecret();
        String afterRotation = restTemplate.exchange(
                url("/api/v1/integrations/attendance-connectors/" + site.connector().id() + "/signing-secret"),
                HttpMethod.PUT, bearer(site.token(), Map.of("signingSecret", rotated)), String.class).getBody();
        assertThat(afterRotation).doesNotContain(rotated);
        assertThat(deliver(path, site.secret(), body, String.class).getStatusCode())
                .as("secreto anterior").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver(path, rotated, body, BatchDto.class).getBody()).isEqualTo(new BatchDto(1, 1, 0, 0));

        ResponseEntity<Void> deleted = restTemplate.exchange(
                url("/api/v1/integrations/attendance-connectors/" + site.connector().id()), HttpMethod.DELETE,
                bearer(site.token(), null), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deliver(path, rotated, body, String.class).getStatusCode())
                .as("conector borrado").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("administrar conectores exige integration:manage: ni sin sesion ni un gerente de RRHH")
    void managingConnectorsRequiresThePermission() {
        Site site = provisionSite("att-perm");
        AdminTestFixture.Provisioned hrManager = fixture.provisionHrManager(site.tenantId(), "att-perm-hr");
        String hrToken = login(hrManager.email(), hrManager.password());
        String connectors = url("/api/v1/integrations/attendance-connectors");

        assertThat(restTemplate.exchange(connectors, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(connectors, HttpMethod.GET, bearer(hrToken, null),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(connectors, HttpMethod.POST, bearer(hrToken, Map.of("name", "Intento")),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(replaceSecret(site.token(), site.connector().id(), "corto", String.class).getStatusCode())
                .as("secreto demasiado corto").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private List<ConnectorDto> listConnectors(String token) {
        return List.of(restTemplate.exchange(url("/api/v1/integrations/attendance-connectors"), HttpMethod.GET,
                bearer(token, null), ConnectorDto[].class).getBody());
    }
}
