package com.ohmdevs.hrsaas.contracts;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
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
 * Aislamiento cruzado de la API administrativa nueva (regla no negociable del
 * proyecto: "todo módulo nuevo con datos de tenant necesita su test de
 * aislamiento cruzado"), mismo estilo que {@code portal.PortalIsolationTest}.
 *
 * <p>Dos tenants sembrados, cada uno con su propio gerente de RRHH, empresa,
 * empleado y contrato. El gerente del tenant A no debe poder leer nada del
 * tenant B por ningún endpoint de {@code org.web}/{@code people.web}/
 * {@code contracts.web} — ni en el listado (RLS + {@code @TenantId} ya lo
 * filtran ahí) ni pidiendo el id ajeno directamente (donde solo protege que
 * la consulta por id también pasa por Hibernate con el tenant del actor
 * fijado, nunca un {@code SELECT} sin filtrar).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminIsolationTest extends AbstractPostgresTest {

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

    private record EmployeeDto(Long id, String firstName, String lastName, String fullName, String email,
                                String phone, String birthDate, String documentType, String maskedDocumentNumber,
                                String status, String createdAt) {
    }

    private record EmployeePageDto(List<EmployeeDto> items, String nextCursor) {
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
    @DisplayName("dos tenants distintos: el gerente de RRHH de uno no ve ni empresas ni empleados del otro")
    void differentTenantsDoNotLeakThroughAdminApi() {
        AdminTestFixture.Provisioned tenantA = fixture.provisionHrManager("iso-a");
        AdminTestFixture.Provisioned tenantB = fixture.provisionHrManager("iso-b");
        String tokenA = login(tenantA.email(), tenantA.password());
        String tokenB = login(tenantB.email(), tenantB.password());

        ResponseEntity<CompanyDto> companyB = restTemplate.exchange(api("/org/companies"), HttpMethod.POST,
                bearerWithBody(tokenB, Map.of("legalName", "Beta Industrial, SRL", "tradeName", "Beta", "rnc", "109988776")),
                CompanyDto.class);
        assertThat(companyB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long companyBId = companyB.getBody().id();

        ResponseEntity<EmployeeDto> employeeB = restTemplate.exchange(api("/employees"), HttpMethod.POST,
                bearerWithBody(tokenB, Map.of(
                        "firstName", "Beatriz", "lastName", "Nunez", "birthDate", "1992-06-20",
                        "documentType", "CEDULA", "documentNumber", "402-9876543-2", "email", "beatriz@beta.test")),
                EmployeeDto.class);
        assertThat(employeeB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long employeeBId = employeeB.getBody().id();

        // El tenant A no ve la empresa de B en su listado...
        ResponseEntity<CompanyDto[]> companiesUnderA = restTemplate.exchange(
                api("/org/companies"), HttpMethod.GET, bearer(tokenA), CompanyDto[].class);
        assertThat(List.of(companiesUnderA.getBody())).extracting(CompanyDto::id).doesNotContain(companyBId);

        // ...ni pidiéndola directo por id.
        ResponseEntity<String> companyBUnderA = restTemplate.exchange(
                api("/org/companies/" + companyBId), HttpMethod.GET, bearer(tokenA), String.class);
        assertThat(companyBUnderA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Mismo par de comprobaciones para el expediente del empleado.
        ResponseEntity<EmployeePageDto> employeesUnderA = restTemplate.exchange(
                api("/employees"), HttpMethod.GET, bearer(tokenA), EmployeePageDto.class);
        assertThat(employeesUnderA.getBody().items()).extracting(EmployeeDto::id).doesNotContain(employeeBId);

        ResponseEntity<String> employeeBUnderA = restTemplate.exchange(
                api("/employees/" + employeeBId), HttpMethod.GET, bearer(tokenA), String.class);
        assertThat(employeeBUnderA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Y en la dirección opuesta, por completitud (aislamiento no es una relación de un solo sentido).
        ResponseEntity<String> employeeBUnderItsOwnTenant = restTemplate.exchange(
                api("/employees/" + employeeBId), HttpMethod.GET, bearer(tokenB), String.class);
        assertThat(employeeBUnderItsOwnTenant.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
