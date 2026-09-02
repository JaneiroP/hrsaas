package com.ohmdevs.hrsaas.portal;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra HTTP real (puerto aleatorio, sin mocks de
 * seguridad): login emite un JWT propio, el resource server lo valida, y
 * cada endpoint de {@code portal} responde exactamente la forma acordada con
 * el frontend ({@code hrsaas-frontend/src/app/core/mock/mock-api.interceptor.ts},
 * que es el contrato de facto hasta que exista OpenAPI publicado).
 *
 * <p>No usa {@code @WithMockUser} ni sustituye el filtro de seguridad por
 * nada: es la única forma de probar que {@code SecurityConfiguration},
 * {@code ActorJwtAuthenticationConverter} y {@code PlatformContextFilter}
 * encajan juntos, no solo cada uno por separado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalApiIntegrationTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PortalTestFixture fixture;

    private record LoginResponseDto(String accessToken, String tokenType, long expiresIn) {
    }

    private record ProfileDto(String id, String fullName, String initials, String position, String department,
                               String location, String supervisorName, String email, String hireDateIso,
                               String contractType, String companyName, String companyRnc, List<String> scopes) {
    }

    private record PayslipSummaryDto(String id, String period, String payDateIso, String status,
                                      BigDecimal grossAmount, BigDecimal netAmount) {
    }

    private record PayslipPageDto(List<PayslipSummaryDto> items, String nextCursor) {
    }

    private record PayslipLineDto(String label, BigDecimal amount) {
    }

    private record PayslipDetailDto(String id, String period, String payDateIso, String status,
                                     BigDecimal grossAmount, BigDecimal netAmount, List<PayslipLineDto> earnings,
                                     List<PayslipLineDto> deductions, BigDecimal totalDeductions,
                                     String companyName, String maskedNationalId) {
    }

    private record LeaveBalanceDto(String type, String label, BigDecimal totalDays, BigDecimal takenDays,
                                    BigDecimal availableDays, String note) {
    }

    private record LeaveRequestDto(String id, String type, String typeLabel, String startDateIso, String endDateIso,
                                    BigDecimal days, String status, String reason, String requestedAtIso) {
    }

    private record LeaveRequestPageDto(List<LeaveRequestDto> items, String nextCursor) {
    }

    private record NewLeaveRequestDto(String type, String startDateIso, String endDateIso, String reason) {
    }

    private String api(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private static HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    @DisplayName("login, perfil, volantes y licencias: el flujo completo del Sprint 1 del portal")
    void fullSelfServiceFlow() {
        PortalTestFixture.Provisioned fx = fixture.provisionEmployee("faro");

        // Sin token: 401 con cuerpo problem+json, no un cierre de conexión ni un 403 genérico.
        ResponseEntity<String> unauthorized = restTemplate.getForEntity(api("/me"), String.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Credenciales incorrectas.
        ResponseEntity<String> badLogin = restTemplate.postForEntity(
                api("/auth/login"), Map.of("email", fx.email(), "password", "incorrecta"), String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Login correcto.
        ResponseEntity<LoginResponseDto> login = restTemplate.postForEntity(
                api("/auth/login"), Map.of("email", fx.email(), "password", fx.password()), LoginResponseDto.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDto loginBody = login.getBody();
        assertThat(loginBody).isNotNull();
        assertThat(loginBody.accessToken()).isNotBlank();
        assertThat(loginBody.tokenType()).isEqualTo("Bearer");
        assertThat(loginBody.expiresIn()).isEqualTo(900L);
        String token = loginBody.accessToken();

        // GET /me
        ResponseEntity<ProfileDto> profile = restTemplate.exchange(api("/me"), HttpMethod.GET, bearer(token), ProfileDto.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProfileDto profileBody = profile.getBody();
        assertThat(profileBody).isNotNull();
        assertThat(profileBody.id()).isEqualTo(fx.employeeId().toString());
        assertThat(profileBody.fullName()).isEqualTo("Valeria Nunez");
        assertThat(profileBody.initials()).isEqualTo("VN");
        assertThat(profileBody.position()).isEqualTo("Analista de Compras");
        assertThat(profileBody.department()).isEqualTo("Adquisiciones");
        assertThat(profileBody.companyName()).isEqualTo("Constructora Faro, SRL");
        assertThat(profileBody.companyRnc()).isEqualTo("131452109");
        assertThat(profileBody.contractType()).isEqualTo("Indefinido");
        assertThat(profileBody.hireDateIso()).isEqualTo("2023-02-03");
        assertThat(profileBody.scopes()).containsExactly("SELF");

        // GET /me/payslips
        ResponseEntity<PayslipPageDto> payslips = restTemplate.exchange(
                api("/me/payslips?limit=10"), HttpMethod.GET, bearer(token), PayslipPageDto.class);
        assertThat(payslips.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payslips.getBody()).isNotNull();
        assertThat(payslips.getBody().items()).hasSize(1);
        PayslipSummaryDto summary = payslips.getBody().items().get(0);
        assertThat(summary.id()).isEqualTo(fx.payslipId().toString());
        assertThat(summary.status()).isEqualTo("PAGADO");
        assertThat(summary.netAmount()).isEqualByComparingTo("48926.80");
        assertThat(payslips.getBody().nextCursor()).isNull();

        // GET /me/payslips/{id}
        ResponseEntity<PayslipDetailDto> detail = restTemplate.exchange(
                api("/me/payslips/" + fx.payslipId()), HttpMethod.GET, bearer(token), PayslipDetailDto.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        PayslipDetailDto detailBody = detail.getBody();
        assertThat(detailBody).isNotNull();
        assertThat(detailBody.earnings()).extracting(PayslipLineDto::label).containsExactly("Salario base");
        assertThat(detailBody.deductions()).hasSize(2);
        assertThat(detailBody.totalDeductions()).isEqualByComparingTo("3073.20");
        assertThat(detailBody.grossAmount()).isEqualByComparingTo("52000.00");
        assertThat(detailBody.netAmount()).isEqualByComparingTo("48926.80");
        assertThat(detailBody.companyName()).isEqualTo("Faro");
        assertThat(detailBody.maskedNationalId()).matches("402-X{7}-1");

        // Un id que no existe: 404, no un 500 ni un id filtrado a ciegas.
        ResponseEntity<String> missingPayslip = restTemplate.exchange(
                api("/me/payslips/999999999"), HttpMethod.GET, bearer(token), String.class);
        assertThat(missingPayslip.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // GET /me/leave-balances
        ResponseEntity<LeaveBalanceDto[]> balances = restTemplate.exchange(
                api("/me/leave-balances"), HttpMethod.GET, bearer(token), LeaveBalanceDto[].class);
        assertThat(balances.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balances.getBody()).isNotNull();
        assertThat(List.of(balances.getBody())).extracting(LeaveBalanceDto::type)
                .containsExactlyInAnyOrder("VACACIONES", "LICENCIA_MEDICA", "PERMISO_PERSONAL");
        LeaveBalanceDto vacationBalance = List.of(balances.getBody()).stream()
                .filter(b -> b.type().equals("VACACIONES")).findFirst().orElseThrow();
        assertThat(vacationBalance.totalDays()).isEqualByComparingTo("14.00");
        assertThat(vacationBalance.availableDays()).isEqualByComparingTo("14.00");
        LeaveBalanceDto medicalBalance = List.of(balances.getBody()).stream()
                .filter(b -> b.type().equals("LICENCIA_MEDICA")).findFirst().orElseThrow();
        assertThat(medicalBalance.totalDays()).isNull();
        assertThat(medicalBalance.availableDays()).isNull();

        // POST /me/leave-requests — lunes a viernes, sin feriados sembrados: 5 días hábiles.
        NewLeaveRequestDto newRequest = new NewLeaveRequestDto(
                "VACACIONES", "2026-11-02", "2026-11-06", "Vacaciones de fin de año");
        ResponseEntity<LeaveRequestDto> created = restTemplate.postForEntity(
                api("/me/leave-requests"), new HttpEntity<>(newRequest, bearer(token).getHeaders()), LeaveRequestDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LeaveRequestDto createdBody = created.getBody();
        assertThat(createdBody).isNotNull();
        assertThat(createdBody.status()).isEqualTo("PENDIENTE");
        assertThat(createdBody.days()).isEqualByComparingTo("5");
        assertThat(createdBody.typeLabel()).isEqualTo("Vacaciones");

        // Rango invertido: 422, no 400 ni 500.
        ResponseEntity<String> invalidRange = restTemplate.postForEntity(
                api("/me/leave-requests"),
                new HttpEntity<>(new NewLeaveRequestDto("VACACIONES", "2026-11-06", "2026-11-02", null), bearer(token).getHeaders()),
                String.class);
        assertThat(invalidRange.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // GET /me/leave-requests: la recién creada aparece primero (orden por id DESC).
        ResponseEntity<LeaveRequestPageDto> requests = restTemplate.exchange(
                api("/me/leave-requests"), HttpMethod.GET, bearer(token), LeaveRequestPageDto.class);
        assertThat(requests.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requests.getBody()).isNotNull();
        assertThat(requests.getBody().items().get(0).id()).isEqualTo(createdBody.id());

        // DELETE /me/leave-requests/{id} — cancela mientras está pendiente.
        ResponseEntity<Void> cancel = restTemplate.exchange(
                api("/me/leave-requests/" + createdBody.id()), HttpMethod.DELETE, bearer(token), Void.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Cancelarla otra vez: ya no está PENDIENTE -> 409, no un 204 silencioso.
        ResponseEntity<String> cancelAgain = restTemplate.exchange(
                api("/me/leave-requests/" + createdBody.id()), HttpMethod.DELETE, bearer(token), String.class);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
