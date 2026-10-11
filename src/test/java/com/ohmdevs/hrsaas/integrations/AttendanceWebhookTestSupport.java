package com.ohmdevs.hrsaas.integrations;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.AdminTestFixture;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import com.ohmdevs.hrsaas.time.infrastructure.TimeEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo común a los tests del webhook de asistencia: sembrar un tenant con su
 * propietario, un empleado y un conector, y enviar lotes firmados como lo
 * haría el sistema de asistencia.
 *
 * <p>La firma se calcula aquí con {@code javax.crypto} directamente, no con
 * {@code WebhookSignatureVerifier}: un test que firmara con el mismo código
 * que verifica pasaría igual si los dos estuvieran mal de la misma manera.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
abstract class AttendanceWebhookTestSupport extends AbstractPostgresTest {

    static final String SIGNATURE_HEADER = "X-Attendance-Signature";

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected AdminTestFixture fixture;

    @Autowired
    private EmployeeRecordService employeeRecords;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    protected record LoginResponseDto(String accessToken, String tokenType, long expiresIn) {
    }

    protected record ConnectorDto(UUID id, String name, boolean hasSigningSecret,
                                  String webhookPath, String createdAt, String lastReceivedAt) {
    }

    protected record BatchDto(int received, int recorded, int duplicates, int skipped) {
    }

    /** Un tenant listo para recibir marcajes: propietario con sesión, un empleado y un conector con secreto. */
    protected record Site(Long tenantId, String token, Long employeeId, ConnectorDto connector, String secret) {
    }

    protected Site provisionSite(String slugPrefix) {
        AdminTestFixture.Provisioned owner = fixture.provisionOwner(slugPrefix);
        String token = login(owner.email(), owner.password());
        Long employeeId = createEmployee(owner.tenantId());
        String secret = newSecret();
        ConnectorDto connector = createConnector(token, "Relojes " + slugPrefix);
        connector = replaceSecret(token, connector.id(), secret).getBody();
        return new Site(owner.tenantId(), token, employeeId, connector, secret);
    }

    protected String url(String pathFromRoot) {
        return "http://localhost:" + port + pathFromRoot;
    }

    protected String login(String email, String password) {
        ResponseEntity<LoginResponseDto> login = restTemplate.postForEntity(
                url("/api/v1/auth/login"), Map.of("email", email, "password", password), LoginResponseDto.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    protected static HttpEntity<Object> bearer(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    protected Long createEmployee(Long tenantId) {
        String unique = UUID.randomUUID().toString().substring(0, 7);
        return TenantContext.callWith(tenantId, () -> employeeRecords.createEmployee("Ana", "Perez",
                LocalDate.of(1990, 1, 1), DocumentType.CEDULA, "001-" + unique + "-8",
                "ana." + unique + "@empresa.test", "809-000-0000").getId());
    }

    protected ConnectorDto createConnector(String token, String name) {
        ResponseEntity<ConnectorDto> created = restTemplate.exchange(
                url("/api/v1/integrations/attendance-connectors"), HttpMethod.POST,
                bearer(token, Map.of("name", name)), ConnectorDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    protected ResponseEntity<ConnectorDto> replaceSecret(String token, UUID connectorId, String secret) {
        return replaceSecret(token, connectorId, secret, ConnectorDto.class);
    }

    /** Con el tipo de respuesta a elegir: una respuesta de error no se puede leer como conector. */
    protected <T> ResponseEntity<T> replaceSecret(String token, UUID connectorId, String secret,
                                                  Class<T> responseType) {
        return restTemplate.exchange(
                url("/api/v1/integrations/attendance-connectors/" + connectorId + "/signing-secret"), HttpMethod.PUT,
                bearer(token, Map.of("signingSecret", secret)), responseType);
    }

    /** Con la forma de los secretos que genera el emisor. */
    protected static String newSecret() {
        return "whsec_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().substring(0, 11);
    }

    /** Un evento con todos los campos que publica el emisor, incluidos los que este sistema no usa. */
    protected static String event(Object employeeId, String deviceId, String punchedAtUtc, String type) {
        return """
                {"eventId":"%s","punchId":"%s","deviceId":"%s","siteCode":"HQ","employeeId":"%s",\
                "punchedAtLocal":"2026-09-01T07:58:00","punchedAtUtc":"%s","type":"%s",\
                "recordedAt":"2026-09-01T11:58:03.412Z"}""".formatted(
                UUID.randomUUID(), UUID.randomUUID(), deviceId, employeeId, punchedAtUtc, type);
    }

    protected static String batch(String... events) {
        return "{\"events\":[" + String.join(",", events) + "]}";
    }

    /** Envía un lote firmado ahora mismo con el secreto dado, a la ruta dada. */
    protected <T> ResponseEntity<T> deliver(String webhookPath, String secret, String body, Class<T> responseType) {
        return deliver(webhookPath, signature(secret, Instant.now().getEpochSecond(), body), body, responseType, true);
    }

    protected <T> ResponseEntity<T> deliver(String webhookPath, String signatureHeader, String body,
                                            Class<T> responseType, boolean signed) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signed) {
            headers.set(SIGNATURE_HEADER, signatureHeader);
        }
        return restTemplate.exchange(url(webhookPath), HttpMethod.POST,
                new HttpEntity<>(body.getBytes(StandardCharsets.UTF_8), headers), responseType);
    }

    protected static String signature(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Los marcajes guardados de un empleado, leídos bajo el tenant indicado. */
    protected List<TimeEntry> timeEntries(Long tenantId, Long employeeId) {
        return TenantContext.callWith(tenantId, () ->
                timeEntryRepository.findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                        employeeId, Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z")));
    }
}
