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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra HTTP real, mismo estilo que {@code portal.PortalApiIntegrationTest}:
 * login emite un JWT propio, el resource server lo valida, y cada endpoint
 * administrativo nuevo ({@code org.web}, {@code people.web},
 * {@code contracts.web}) responde lo que promete su Javadoc.
 *
 * <p>Cubre el flujo completo de alta de un empleado desde cero — empresa,
 * departamento, cargo, expediente, contratación, cambio de salario,
 * promoción, suspensión/reactivación y desvinculación — porque es exactamente
 * la secuencia que el equipo de frontend va a construir contra esta API.
 * También cubre el rechazo por falta de permiso (rol {@code EMPLOYEE}, alcance
 * {@code SELF}) y un cuerpo inválido (400 de Bean Validation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminApiIntegrationTest extends AbstractPostgresTest {

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

    private record DepartmentDto(Long id, Long companyId, String name, boolean active) {
    }

    private record PositionDto(Long id, Long companyId, Long departmentId, String departmentName,
                                String title, Long payGradeId, boolean active) {
    }

    private record EmployeeDto(Long id, String firstName, String lastName, String fullName, String email,
                                String phone, String birthDate, String documentType, String maskedDocumentNumber,
                                String status, String createdAt) {
    }

    private record EmployeePageDto(List<EmployeeDto> items, String nextCursor) {
    }

    private record ContractDto(Long id, Long employeeId, String employeeName, String employeeMaskedDocumentNumber,
                                Long companyId, String companyName, Long positionId, String positionTitle,
                                Long costCenterId, Long supervisorEmployeeId, String contractType,
                                String startDate, String endDate, String probationEndsAt, String status,
                                BigDecimal currentSalary, String currency, String createdAt) {
    }

    private record ContractPageDto(List<ContractDto> items, String nextCursor) {
    }

    private record SalaryHistoryDto(Long id, BigDecimal amount, String currency,
                                     String effectiveFrom, String effectiveTo, String reason) {
    }

    private record TerminationDto(Long id, String terminationDate, String cause,
                                   boolean noticeGiven, boolean severanceEligible, String notes) {
    }

    private record ContractDetailDto(ContractDto contract, List<SalaryHistoryDto> salaryHistory,
                                      List<Object> amendments, TerminationDto termination) {
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
    @DisplayName("un gerente de RRHH arma la estructura, contrata, cambia salario, promueve, suspende y desvincula a un empleado")
    void fullHrLifecycle() {
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager("faro-admin");
        String token = login(hr.email(), hr.password());

        // --- org: empresa, departamento, cargo ---------------------------------
        ResponseEntity<CompanyDto> company = restTemplate.exchange(api("/org/companies"), HttpMethod.POST,
                bearerWithBody(token, Map.of("legalName", "Constructora Alfa, SRL", "tradeName", "Alfa", "rnc", "101234567")),
                CompanyDto.class);
        assertThat(company.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long companyId = company.getBody().id();

        ResponseEntity<CompanyDto[]> companies = restTemplate.exchange(
                api("/org/companies"), HttpMethod.GET, bearer(token), CompanyDto[].class);
        assertThat(companies.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(List.of(companies.getBody())).extracting(CompanyDto::id).contains(companyId);

        ResponseEntity<DepartmentDto> department = restTemplate.exchange(
                api("/org/companies/" + companyId + "/departments"), HttpMethod.POST,
                bearerWithBody(token, Map.of("name", "Operaciones")), DepartmentDto.class);
        assertThat(department.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long departmentId = department.getBody().id();

        ResponseEntity<PositionDto> position = restTemplate.exchange(
                api("/org/companies/" + companyId + "/positions"), HttpMethod.POST,
                bearerWithBody(token, Map.of("departmentId", departmentId, "title", "Analista de Compras")), PositionDto.class);
        assertThat(position.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long positionId = position.getBody().id();
        assertThat(position.getBody().departmentName()).isEqualTo("Operaciones");

        ResponseEntity<PositionDto> managerPosition = restTemplate.exchange(
                api("/org/companies/" + companyId + "/positions"), HttpMethod.POST,
                bearerWithBody(token, Map.of("departmentId", departmentId, "title", "Gerente de Operaciones")), PositionDto.class);
        Long managerPositionId = managerPosition.getBody().id();

        // --- people: expediente --------------------------------------------------
        ResponseEntity<EmployeeDto> created = restTemplate.exchange(api("/employees"), HttpMethod.POST,
                bearerWithBody(token, Map.of(
                        "firstName", "Alberto", "lastName", "Reyes", "birthDate", "1990-01-15",
                        "documentType", "CEDULA", "documentNumber", "402-1234567-1", "email", "alberto@alfa.test")),
                EmployeeDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long employeeId = created.getBody().id();
        assertThat(created.getBody().status()).isEqualTo("PENDING");
        assertThat(created.getBody().maskedDocumentNumber()).isEqualTo("402-XXXXXXX-1");

        ResponseEntity<EmployeeDto> detail = restTemplate.exchange(
                api("/employees/" + employeeId), HttpMethod.GET, bearer(token), EmployeeDto.class);
        assertThat(detail.getBody().fullName()).isEqualTo("Alberto Reyes");

        ResponseEntity<EmployeeDto> updated = restTemplate.exchange(api("/employees/" + employeeId), HttpMethod.PATCH,
                bearerWithBody(token, Map.of("firstName", "Alberto", "lastName", "Reyes", "email", "alberto@alfa.test",
                        "phone", "809-555-1234")),
                EmployeeDto.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().phone()).isEqualTo("809-555-1234");

        ResponseEntity<EmployeePageDto> employees = restTemplate.exchange(
                api("/employees?limit=10"), HttpMethod.GET, bearer(token), EmployeePageDto.class);
        assertThat(employees.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(employees.getBody().items()).extracting(EmployeeDto::id).contains(employeeId);

        // --- contracts: contratar --------------------------------------------------
        ResponseEntity<ContractDto> hired = restTemplate.exchange(api("/contracts"), HttpMethod.POST,
                bearerWithBody(token, Map.of(
                        "employeeId", employeeId, "companyId", companyId, "positionId", positionId,
                        "contractType", "INDEFINITE", "startDate", "2026-01-01",
                        "initialSalary", new BigDecimal("50000.00"), "currency", "DOP")),
                ContractDto.class);
        assertThat(hired.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long contractId = hired.getBody().id();
        assertThat(hired.getBody().employeeName()).isEqualTo("Alberto Reyes");
        assertThat(hired.getBody().companyName()).isEqualTo("Alfa");
        assertThat(hired.getBody().positionTitle()).isEqualTo("Analista de Compras");
        assertThat(hired.getBody().currentSalary()).isEqualByComparingTo("50000.00");
        assertThat(hired.getBody().status()).isEqualTo("ACTIVE");

        // Contratar marca el expediente como ACTIVE (EmployeeLifecycleService, disparado desde contracts).
        ResponseEntity<EmployeeDto> employeeAfterHire = restTemplate.exchange(
                api("/employees/" + employeeId), HttpMethod.GET, bearer(token), EmployeeDto.class);
        assertThat(employeeAfterHire.getBody().status()).isEqualTo("ACTIVE");

        // --- salario, promoción -----------------------------------------------------
        ResponseEntity<SalaryHistoryDto> raise = restTemplate.exchange(
                api("/contracts/" + contractId + "/salary-changes"), HttpMethod.POST,
                bearerWithBody(token, Map.of("amount", new BigDecimal("55000.00"), "currency", "DOP",
                        "effectiveFrom", "2026-02-01", "reason", "RAISE")),
                SalaryHistoryDto.class);
        assertThat(raise.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ContractDto> promoted = restTemplate.exchange(
                api("/contracts/" + contractId + "/promote"), HttpMethod.POST,
                bearerWithBody(token, Map.of("newPositionId", managerPositionId, "effectiveDate", "2026-03-01",
                        "notes", "Ascenso por desempeño")),
                ContractDto.class);
        assertThat(promoted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promoted.getBody().positionTitle()).isEqualTo("Gerente de Operaciones");

        ResponseEntity<ContractDetailDto> contractDetail = restTemplate.exchange(
                api("/contracts/" + contractId), HttpMethod.GET, bearer(token), ContractDetailDto.class);
        assertThat(contractDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contractDetail.getBody().salaryHistory()).hasSize(2);
        assertThat(contractDetail.getBody().contract().currentSalary()).isEqualByComparingTo("55000.00");

        // --- suspensión / reactivación ------------------------------------------------
        ResponseEntity<ContractDto> suspended = restTemplate.exchange(
                api("/contracts/" + contractId + "/suspend"), HttpMethod.POST, bearer(token), ContractDto.class);
        assertThat(suspended.getBody().status()).isEqualTo("SUSPENDED");

        ResponseEntity<ContractDto> reactivated = restTemplate.exchange(
                api("/contracts/" + contractId + "/reactivate"), HttpMethod.POST, bearer(token), ContractDto.class);
        assertThat(reactivated.getBody().status()).isEqualTo("ACTIVE");

        // --- filtro por empresa y por empleado en el listado global ------------------
        ResponseEntity<ContractPageDto> byCompany = restTemplate.exchange(
                api("/contracts?companyId=" + companyId), HttpMethod.GET, bearer(token), ContractPageDto.class);
        assertThat(byCompany.getBody().items()).extracting(ContractDto::id).contains(contractId);

        ResponseEntity<ContractPageDto> byEmployee = restTemplate.exchange(
                api("/contracts?employeeId=" + employeeId), HttpMethod.GET, bearer(token), ContractPageDto.class);
        assertThat(byEmployee.getBody().items()).hasSize(1);

        // --- desvinculación ------------------------------------------------------------
        ResponseEntity<TerminationDto> termination = restTemplate.exchange(
                api("/contracts/" + contractId + "/terminate"), HttpMethod.POST,
                bearerWithBody(token, Map.of("terminationDate", "2026-04-01", "cause", "RESIGNATION",
                        "noticeGiven", true, "severanceEligible", false, "notes", "Renuncia voluntaria")),
                TerminationDto.class);
        assertThat(termination.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(termination.getBody().cause()).isEqualTo("RESIGNATION");

        ResponseEntity<EmployeeDto> employeeAfterTermination = restTemplate.exchange(
                api("/employees/" + employeeId), HttpMethod.GET, bearer(token), EmployeeDto.class);
        assertThat(employeeAfterTermination.getBody().status()).isEqualTo("INACTIVE");

        ResponseEntity<ContractDetailDto> finalDetail = restTemplate.exchange(
                api("/contracts/" + contractId), HttpMethod.GET, bearer(token), ContractDetailDto.class);
        assertThat(finalDetail.getBody().contract().status()).isEqualTo("TERMINATED");
        assertThat(finalDetail.getBody().termination()).isNotNull();

        // --- filtro por estado en el listado de empleados ------------------------------
        ResponseEntity<EmployeePageDto> inactiveEmployees = restTemplate.exchange(
                api("/employees?status=INACTIVE"), HttpMethod.GET, bearer(token), EmployeePageDto.class);
        assertThat(inactiveEmployees.getBody().items()).extracting(EmployeeDto::id).contains(employeeId);
    }

    @Test
    @DisplayName("un cuerpo invalido (sin nombre) se rechaza con 400 antes de tocar la base de datos")
    void invalidEmployeeBodyIsRejected() {
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager("faro-admin-invalid");
        String token = login(hr.email(), hr.password());

        ResponseEntity<String> response = restTemplate.exchange(api("/employees"), HttpMethod.POST,
                bearerWithBody(token, Map.of(
                        "firstName", "", "lastName", "Reyes", "birthDate", "1990-01-15",
                        "documentType", "CEDULA", "documentNumber", "402-1234567-2")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("un empleado raso (alcance SELF) no puede listar expedientes por la API administrativa")
    void plainEmployeeIsForbiddenFromAdminApi() {
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager("faro-admin-forbidden");
        AdminTestFixture.Provisioned worker = fixture.provisionPlainEmployee(hr.tenantId(), "faro-admin-forbidden");
        String workerToken = login(worker.email(), worker.password());

        ResponseEntity<String> response = restTemplate.exchange(
                api("/employees"), HttpMethod.GET, bearer(workerToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> contractsResponse = restTemplate.exchange(
                api("/contracts"), HttpMethod.GET, bearer(workerToken), String.class);
        assertThat(contractsResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> companiesResponse = restTemplate.exchange(
                api("/org/companies"), HttpMethod.GET, bearer(workerToken), String.class);
        assertThat(companiesResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
