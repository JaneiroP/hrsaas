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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra HTTP real, mismo estilo que
 * {@code contracts.AdminApiIntegrationTest}: login emite un JWT propio, y
 * cada endpoint nuevo de {@code payroll.web.PayrollAdminController} responde
 * lo que promete su Javadoc.
 *
 * <p>Cubre el ciclo feliz completo (período → corrida → cálculo → volante →
 * revisión → aprobación → pago → cierre), la transición ilegal (aprobar una
 * corrida en {@code BORRADOR}), el maker-checker de {@code HR_MANAGER} (403 al
 * intentar aprobar o revertir — ver Javadoc de {@code SystemRole#HR_MANAGER})
 * y el reverso de una corrida ya cerrada (AD-03: la original nunca se toca).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PayrollAdminApiIntegrationTest extends AbstractPostgresTest {

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

    private record ContractDto(Long id, Long employeeId, String employeeName, String employeeMaskedDocumentNumber,
                                Long companyId, String companyName, Long positionId, String positionTitle,
                                Long costCenterId, Long supervisorEmployeeId, String contractType,
                                String startDate, String endDate, String probationEndsAt, String status,
                                BigDecimal currentSalary, String currency, String createdAt) {
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

    private record PayslipLineDto(String label, BigDecimal amount) {
    }

    private record PayslipAdminSummaryDto(Long payslipId, Long employeeId, Long contractId, Long costCenterId,
                                           BigDecimal grossPay, BigDecimal netPay, String currency) {
    }

    private record PayslipAdminDetailDto(Long payslipId, Long employeeId, Long contractId, Long costCenterId,
                                          BigDecimal grossPay, BigDecimal netPay, String currency,
                                          List<PayslipLineDto> earnings, List<PayslipLineDto> deductions,
                                          BigDecimal totalDeductions) {
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

    private Long createCompany(String token, String rnc) {
        ResponseEntity<CompanyDto> company = restTemplate.exchange(api("/org/companies"), HttpMethod.POST,
                bearerWithBody(token, Map.of("legalName", "Nomina Alfa, SRL", "tradeName", "Nomina Alfa", "rnc", rnc)),
                CompanyDto.class);
        assertThat(company.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return company.getBody().id();
    }

    @Test
    @DisplayName("periodo -> corrida -> calculo -> volante -> revision -> aprobacion (OWNER) -> pago -> cierre")
    void fullPayrollAdminLifecycleProducesAPayslipAndReachesClosed() {
        AdminTestFixture.Provisioned owner = fixture.provisionOwner("payroll-admin");
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager(owner.tenantId(), "payroll-admin");
        String hrToken = login(hr.email(), hr.password());
        String ownerToken = login(owner.email(), owner.password());

        Long companyId = createCompany(hrToken, "101990001");

        ResponseEntity<DepartmentDto> department = restTemplate.exchange(
                api("/org/companies/" + companyId + "/departments"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("name", "Nomina y Pagos")), DepartmentDto.class);
        Long departmentId = department.getBody().id();

        ResponseEntity<PositionDto> position = restTemplate.exchange(
                api("/org/companies/" + companyId + "/positions"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("departmentId", departmentId, "title", "Analista de Nomina")),
                PositionDto.class);
        Long positionId = position.getBody().id();

        ResponseEntity<EmployeeDto> employee = restTemplate.exchange(api("/employees"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of(
                        "firstName", "Carla", "lastName", "Mateo", "birthDate", "1988-05-20",
                        "documentType", "CEDULA", "documentNumber", "402-1112223-4", "email", "carla@alfa.test")),
                EmployeeDto.class);
        Long employeeId = employee.getBody().id();

        ResponseEntity<ContractDto> contract = restTemplate.exchange(api("/contracts"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of(
                        "employeeId", employeeId, "companyId", companyId, "positionId", positionId,
                        "contractType", "INDEFINITE", "startDate", "2026-01-01",
                        "initialSalary", new BigDecimal("50000.00"), "currency", "DOP")),
                ContractDto.class);
        assertThat(contract.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long contractId = contract.getBody().id();

        // --- periodo -----------------------------------------------------------------
        ResponseEntity<PayrollPeriodDto> period = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/periods"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("name", "Enero 2026", "frequency", "MONTHLY",
                        "startDate", "2026-01-01", "endDate", "2026-01-31", "payDate", "2026-02-05")),
                PayrollPeriodDto.class);
        assertThat(period.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long periodId = period.getBody().id();
        assertThat(period.getBody().frequency()).isEqualTo("MONTHLY");

        ResponseEntity<PayrollPeriodDto[]> periods = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/periods"), HttpMethod.GET, bearer(hrToken), PayrollPeriodDto[].class);
        assertThat(List.of(periods.getBody())).extracting(PayrollPeriodDto::id).contains(periodId);

        // --- corrida: creacion y calculo -----------------------------------------------
        ResponseEntity<PayrollRunDto> run = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/runs"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("payrollPeriodId", periodId)), PayrollRunDto.class);
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long runId = run.getBody().id();
        assertThat(run.getBody().status()).isEqualTo("BORRADOR");
        assertThat(run.getBody().periodName()).isEqualTo("Enero 2026");

        ResponseEntity<PayrollRunDto[]> runsByCompany = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/runs"), HttpMethod.GET, bearer(hrToken), PayrollRunDto[].class);
        assertThat(List.of(runsByCompany.getBody())).extracting(PayrollRunDto::id).contains(runId);

        ResponseEntity<PayrollRunDto> calculated = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/calculate"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        assertThat(calculated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calculated.getBody().status()).isEqualTo("CALCULADA");
        assertThat(calculated.getBody().calculatedAt()).isNotNull();

        ResponseEntity<PayrollRunDto> runDetail = restTemplate.exchange(
                api("/payroll/runs/" + runId), HttpMethod.GET, bearer(hrToken), PayrollRunDto.class);
        assertThat(runDetail.getBody().status()).isEqualTo("CALCULADA");

        // --- volantes: sin ningun PayrollConcept configurado, el bruto es solo el salario base ---
        ResponseEntity<PayslipAdminSummaryDto[]> payslips = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/payslips"), HttpMethod.GET, bearer(hrToken), PayslipAdminSummaryDto[].class);
        assertThat(payslips.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payslips.getBody()).hasSize(1);
        PayslipAdminSummaryDto payslipSummary = payslips.getBody()[0];
        assertThat(payslipSummary.employeeId()).isEqualTo(employeeId);
        assertThat(payslipSummary.contractId()).isEqualTo(contractId);
        assertThat(payslipSummary.grossPay()).isEqualByComparingTo("50000.00");
        assertThat(payslipSummary.netPay()).isEqualByComparingTo("50000.00");

        ResponseEntity<PayslipAdminDetailDto> payslipDetail = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/payslips/" + payslipSummary.payslipId()),
                HttpMethod.GET, bearer(hrToken), PayslipAdminDetailDto.class);
        assertThat(payslipDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payslipDetail.getBody().employeeId()).isEqualTo(employeeId);
        assertThat(payslipDetail.getBody().earnings()).extracting(PayslipLineDto::label).contains("Salario base");
        assertThat(payslipDetail.getBody().deductions()).isEmpty();
        assertThat(payslipDetail.getBody().totalDeductions()).isEqualByComparingTo("0.00");

        // --- revision, aprobacion (OWNER, maker-checker), pago y cierre ------------------
        ResponseEntity<PayrollRunDto> submitted = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/submit-for-review"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        assertThat(submitted.getBody().status()).isEqualTo("EN_REVISION");

        ResponseEntity<PayrollRunDto> approved = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/approve"), HttpMethod.POST, bearer(ownerToken), PayrollRunDto.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo("APROBADA");
        assertThat(approved.getBody().approvedByUserId()).isEqualTo(owner.userId());

        ResponseEntity<PayrollRunDto> paid = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/mark-paid"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        assertThat(paid.getBody().status()).isEqualTo("PAGADA");

        ResponseEntity<PayrollRunDto> closed = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/close"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        assertThat(closed.getBody().status()).isEqualTo("CERRADA");
    }

    @Test
    @DisplayName("HR_MANAGER (armador de la nomina) recibe 403 al intentar aprobar o revertir: el maker-checker no se relaja")
    void hrManagerIsForbiddenFromApprovingOrReversing() {
        AdminTestFixture.Provisioned owner = fixture.provisionOwner("payroll-403");
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager(owner.tenantId(), "payroll-403");
        String hrToken = login(hr.email(), hr.password());

        Long companyId = createCompany(hrToken, "101990002");
        ResponseEntity<PayrollPeriodDto> period = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/periods"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("name", "Febrero 2026", "frequency", "MONTHLY",
                        "startDate", "2026-02-01", "endDate", "2026-02-28", "payDate", "2026-03-05")),
                PayrollPeriodDto.class);
        Long periodId = period.getBody().id();

        ResponseEntity<PayrollRunDto> run = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/runs"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("payrollPeriodId", periodId)), PayrollRunDto.class);
        Long runId = run.getBody().id();

        ResponseEntity<String> approveAttempt = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/approve"), HttpMethod.POST, bearer(hrToken), String.class);
        assertThat(approveAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> reverseAttempt = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/reverse"), HttpMethod.POST, bearer(hrToken), String.class);
        assertThat(reverseAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("aprobar una corrida en BORRADOR es una transicion ilegal: 409, no 200")
    void approvingADraftRunIsRejectedAsIllegalTransition() {
        AdminTestFixture.Provisioned owner = fixture.provisionOwner("payroll-409");
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager(owner.tenantId(), "payroll-409");
        String hrToken = login(hr.email(), hr.password());
        String ownerToken = login(owner.email(), owner.password());

        Long companyId = createCompany(hrToken, "101990003");
        ResponseEntity<PayrollPeriodDto> period = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/periods"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("name", "Marzo 2026", "frequency", "MONTHLY",
                        "startDate", "2026-03-01", "endDate", "2026-03-31", "payDate", "2026-04-05")),
                PayrollPeriodDto.class);
        Long periodId = period.getBody().id();

        ResponseEntity<PayrollRunDto> run = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/runs"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("payrollPeriodId", periodId)), PayrollRunDto.class);
        Long runId = run.getBody().id();
        assertThat(run.getBody().status()).isEqualTo("BORRADOR");

        ResponseEntity<String> approveAttempt = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/approve"), HttpMethod.POST, bearer(ownerToken), String.class);
        assertThat(approveAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("reverse sobre una corrida ya CERRADA crea una corrida nueva (AD-03): la original nunca se toca")
    void reverseCreatesACompensatingRunAfterClosing() {
        AdminTestFixture.Provisioned owner = fixture.provisionOwner("payroll-reverse");
        AdminTestFixture.Provisioned hr = fixture.provisionHrManager(owner.tenantId(), "payroll-reverse");
        String hrToken = login(hr.email(), hr.password());
        String ownerToken = login(owner.email(), owner.password());

        Long companyId = createCompany(hrToken, "101990004");
        ResponseEntity<PayrollPeriodDto> period = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/periods"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("name", "Abril 2026", "frequency", "MONTHLY",
                        "startDate", "2026-04-01", "endDate", "2026-04-30", "payDate", "2026-05-05")),
                PayrollPeriodDto.class);
        Long periodId = period.getBody().id();

        ResponseEntity<PayrollRunDto> run = restTemplate.exchange(
                api("/payroll/companies/" + companyId + "/runs"), HttpMethod.POST,
                bearerWithBody(hrToken, Map.of("payrollPeriodId", periodId)), PayrollRunDto.class);
        Long runId = run.getBody().id();

        restTemplate.exchange(api("/payroll/runs/" + runId + "/calculate"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        restTemplate.exchange(api("/payroll/runs/" + runId + "/submit-for-review"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        restTemplate.exchange(api("/payroll/runs/" + runId + "/approve"), HttpMethod.POST, bearer(ownerToken), PayrollRunDto.class);
        restTemplate.exchange(api("/payroll/runs/" + runId + "/mark-paid"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        ResponseEntity<PayrollRunDto> closed = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/close"), HttpMethod.POST, bearer(hrToken), PayrollRunDto.class);
        assertThat(closed.getBody().status()).isEqualTo("CERRADA");

        ResponseEntity<PayrollRunDto> reversal = restTemplate.exchange(
                api("/payroll/runs/" + runId + "/reverse"), HttpMethod.POST, bearer(ownerToken), PayrollRunDto.class);
        assertThat(reversal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reversal.getBody().id()).isNotEqualTo(runId);
        assertThat(reversal.getBody().reversalOfRunId()).isEqualTo(runId);
        assertThat(reversal.getBody().status()).isEqualTo("CALCULADA");

        // La original sigue CERRADA, intacta.
        ResponseEntity<PayrollRunDto> originalStillClosed = restTemplate.exchange(
                api("/payroll/runs/" + runId), HttpMethod.GET, bearer(hrToken), PayrollRunDto.class);
        assertThat(originalStillClosed.getBody().status()).isEqualTo("CERRADA");
    }
}
