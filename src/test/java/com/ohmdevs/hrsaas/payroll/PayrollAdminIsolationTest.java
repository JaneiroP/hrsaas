package com.ohmdevs.hrsaas.payroll;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.AdminTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado de la API administrativa de {@code payroll}, mismo
 * estilo que {@code contracts.AdminIsolationTest} (regla no negociable:
 * "todo módulo nuevo con datos de tenant necesita su test de aislamiento
 * cruzado").
 *
 * <p>Dos tenants sembrados, cada uno con su propio gerente de RRHH, empresa,
 * período y corrida. El gerente del tenant A no debe poder leer nada del
 * tenant B — ni en el listado (RLS + {@code @TenantId} ya lo filtran ahí) ni
 * pidiendo el id ajeno directamente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PayrollAdminIsolationTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AdminTestFixture fixture;

    private record LoginResponseDto(String accessToken, String tokenType, long expiresIn) {
    }

    private record CompanyDto(Long id, String legalName, String tradeName, String rnc, boolean active) {
    }

    private record PayrollPeriodDto(Long id, Long companyId, String name, String frequency,
                                     String startDate, String endDate, String payDate) {
    }

    private record PayrollRunDto(Long id, Long companyId, Long payrollPeriodId, String periodName, String frequency,
                                  String periodStartDate, String periodEndDate, String payDate,
                                  String status, String configVersionId, Long reversalOfRunId,
                                  String createdAt, String calculatedAt, String approvedAt, Long approvedByUserId,
                                  String paidAt, String closedAt) {
    }

    private String api(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private String login(String email, String password) {
        ResponseEntity<LoginResponseDto> login = restTemplate.postForEntity(
                api("/auth/login"), Map.of("email", email, "password", password), LoginResponseDto.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private static HttpEntity<Object> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Object> bearerWithBody(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("dos tenants distintos: el gerente de RRHH de uno no ve ni periodos ni corridas del otro")
    void differentTenantsDoNotLeakThroughPayrollAdminApi() {
        AdminTestFixture.Provisioned tenantA = fixture.provisionHrManager("payroll-iso-a");
        AdminTestFixture.Provisioned tenantB = fixture.provisionHrManager("payroll-iso-b");
        String tokenA = login(tenantA.email(), tenantA.password());
        String tokenB = login(tenantB.email(), tenantB.password());

        ResponseEntity<CompanyDto> companyB = restTemplate.exchange(api("/org/companies"), HttpMethod.POST,
                bearerWithBody(tokenB, Map.of("legalName", "Nomina Beta, SRL", "tradeName", "Nomina Beta", "rnc", "109988001")),
                CompanyDto.class);
        assertThat(companyB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long companyBId = companyB.getBody().id();

        ResponseEntity<PayrollPeriodDto> periodB = restTemplate.exchange(
                api("/payroll/companies/" + companyBId + "/periods"), HttpMethod.POST,
                bearerWithBody(tokenB, Map.of("name", "Enero 2026", "frequency", "MONTHLY",
                        "startDate", "2026-01-01", "endDate", "2026-01-31", "payDate", "2026-02-05")),
                PayrollPeriodDto.class);
        assertThat(periodB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long periodBId = periodB.getBody().id();

        ResponseEntity<PayrollRunDto> runB = restTemplate.exchange(
                api("/payroll/companies/" + companyBId + "/runs"), HttpMethod.POST,
                bearerWithBody(tokenB, Map.of("payrollPeriodId", periodBId)), PayrollRunDto.class);
        assertThat(runB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long runBId = runB.getBody().id();

        // El tenant A no ve la empresa de B (precondicion: sin ella, "no ve el periodo"
        // seria trivialmente cierto por el guardia de pertenencia, no por RLS).
        ResponseEntity<String> companyBUnderA = restTemplate.exchange(
                api("/org/companies/" + companyBId), HttpMethod.GET, bearer(tokenA), String.class);
        assertThat(companyBUnderA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ...ni el periodo de B: el mismo company_id consultado bajo el tenant de A
        // no resuelve ninguna fila (RLS filtra payroll_periods por tenant_id, no por
        // si la propia empresa existe bajo ese tenant).
        ResponseEntity<PayrollPeriodDto[]> periodsUnderA = restTemplate.exchange(
                api("/payroll/companies/" + companyBId + "/periods"), HttpMethod.GET, bearer(tokenA), PayrollPeriodDto[].class);
        assertThat(periodsUnderA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(periodsUnderA.getBody()).isEmpty();

        // ...ni la corrida de B, ni en el listado ni pidiendola directo por id.
        ResponseEntity<PayrollRunDto[]> runsUnderA = restTemplate.exchange(
                api("/payroll/companies/" + companyBId + "/runs"), HttpMethod.GET, bearer(tokenA), PayrollRunDto[].class);
        assertThat(List.of(runsUnderA.getBody())).extracting(PayrollRunDto::id).doesNotContain(runBId);

        ResponseEntity<String> runBUnderA = restTemplate.exchange(
                api("/payroll/runs/" + runBId), HttpMethod.GET, bearer(tokenA), String.class);
        assertThat(runBUnderA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Y en la direccion opuesta, por completitud: la corrida de B se ve bajo su propio tenant.
        ResponseEntity<PayrollRunDto> runBUnderItsOwnTenant = restTemplate.exchange(
                api("/payroll/runs/" + runBId), HttpMethod.GET, bearer(tokenB), PayrollRunDto.class);
        assertThat(runBUnderItsOwnTenant.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
