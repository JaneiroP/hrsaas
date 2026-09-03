package com.ohmdevs.hrsaas.portal;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El riesgo que el blueprint §09 nombra explícitamente: no que alguien entre
 * sin credenciales, sino que un usuario legítimo lea el volante o cancele la
 * licencia de otro cambiando un id en la URL.
 *
 * <p>Cubre las dos capas por separado, como {@code PlatformIsolationTest}:
 * <strong>mismo tenant, empleados distintos</strong> (RLS no ayuda aquí —
 * ambas filas son del mismo tenant; solo el filtro por {@code employeeId} en
 * la consulta lo impide) y <strong>tenants distintos</strong> (RLS sí debería
 * bastar, pero se prueba igual: es la última red, no la única).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Boot 4.1.1 dejó de autoconfigurar TestRestTemplate solo por RANDOM_PORT
// (cambio real, no un olvido de la Fase 5 anterior: sin Docker no había
// forma de descubrirlo, porque el test ni siquiera llegaba a arrancar el
// contexto). Sin esta anotación, @Autowired de TestRestTemplate falla con
// NoSuchBeanDefinitionException aunque spring-boot-resttestclient esté en
// el classpath (verificado inspeccionando el jar: TestRestTemplateTestAutoConfiguration
// solo se importa vía este import-selector, no vía el AutoConfiguration.imports general).
@AutoConfigureTestRestTemplate
class PortalIsolationTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PortalTestFixture fixture;

    private record LoginResponseDto(String accessToken, String tokenType, long expiresIn) {
    }

    private String api(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private String loginAndGetToken(String email, String password) {
        ResponseEntity<LoginResponseDto> login = restTemplate.postForEntity(
                api("/auth/login"), Map.of("email", email, "password", password), LoginResponseDto.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    @DisplayName("dos empleados del mismo tenant: ninguno lee ni cancela lo del otro por id")
    void sameTenantEmployeesCannotReachEachOthersData() {
        PortalTestFixture.Provisioned owner = fixture.provisionEmployee("faro-coworkers");
        PortalTestFixture.Coworker coworker = fixture.provisionCoworker(
                owner.tenantId(), owner.companyId(), owner.positionId(), owner.vacationLeaveTypeId(), "faro-coworkers");

        String ownerToken = loginAndGetToken(owner.email(), owner.password());
        String coworkerToken = loginAndGetToken(coworker.email(), coworker.password());

        // El dueño de la sesión no puede leer el volante del compañero...
        ResponseEntity<String> ownerReadsCoworkerPayslip = restTemplate.exchange(
                api("/me/payslips/" + coworker.payslipId()), HttpMethod.GET, bearer(ownerToken), String.class);
        assertThat(ownerReadsCoworkerPayslip.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ...ni al revés.
        ResponseEntity<String> coworkerReadsOwnerPayslip = restTemplate.exchange(
                api("/me/payslips/" + owner.payslipId()), HttpMethod.GET, bearer(coworkerToken), String.class);
        assertThat(coworkerReadsOwnerPayslip.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Cada uno sigue viendo el suyo propio sin problema.
        ResponseEntity<String> ownerReadsOwnPayslip = restTemplate.exchange(
                api("/me/payslips/" + owner.payslipId()), HttpMethod.GET, bearer(ownerToken), String.class);
        assertThat(ownerReadsOwnPayslip.getStatusCode()).isEqualTo(HttpStatus.OK);

        // El dueño de la sesión no puede cancelar la solicitud de licencia del compañero.
        ResponseEntity<String> ownerCancelsCoworkerLeave = restTemplate.exchange(
                api("/me/leave-requests/" + coworker.leaveRequestId()), HttpMethod.DELETE, bearer(ownerToken), String.class);
        assertThat(ownerCancelsCoworkerLeave.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // El propio dueño de la solicitud sí puede.
        ResponseEntity<Void> coworkerCancelsOwnLeave = restTemplate.exchange(
                api("/me/leave-requests/" + coworker.leaveRequestId()), HttpMethod.DELETE, bearer(coworkerToken), Void.class);
        assertThat(coworkerCancelsOwnLeave.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("dos tenants distintos: ningún actor lee datos del otro tenant por ningún endpoint del portal")
    void differentTenantsDoNotLeakThroughPortal() {
        PortalTestFixture.Provisioned tenantA = fixture.provisionEmployee("acme-a");
        PortalTestFixture.Provisioned tenantB = fixture.provisionEmployee("acme-b");

        String tokenA = loginAndGetToken(tenantA.email(), tenantA.password());

        ResponseEntity<String> crossTenantPayslip = restTemplate.exchange(
                api("/me/payslips/" + tenantB.payslipId()), HttpMethod.GET, bearer(tokenA), String.class);
        assertThat(crossTenantPayslip.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String tokenB = loginAndGetToken(tenantB.email(), tenantB.password());
        ResponseEntity<String> reverseCrossTenantPayslip = restTemplate.exchange(
                api("/me/payslips/" + tenantA.payslipId()), HttpMethod.GET, bearer(tokenB), String.class);
        assertThat(reverseCrossTenantPayslip.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
