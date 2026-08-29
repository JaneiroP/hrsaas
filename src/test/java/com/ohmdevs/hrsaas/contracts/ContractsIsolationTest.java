package com.ohmdevs.hrsaas.contracts;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryChangeReason;
import com.ohmdevs.hrsaas.contracts.infrastructure.EmploymentContractRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.SalaryHistoryRepository;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado del módulo {@code contracts}, mismo patrón que el
 * resto de la suite — pero extendido a las tablas de Envers
 * ({@code employment_contracts_aud}, {@code revinfo}), que {@code
 * EnversSchemaSpikeTest} confirmó que NO llevan {@code tenant_id} directo:
 * su aislamiento real depende del {@code JOIN} de
 * {@code V11__contracts_envers_rls.sql}, así que hay que probarlo con una
 * conexión JDBC cruda como el resto de los tests de RLS — un repositorio de
 * Spring Data no ejercita esa política.
 */
class ContractsIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private ContractLifecycleService contracts;
    @Autowired
    private EmploymentContractRepository contractRepository;
    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private EmploymentContract acmeContract;

    @BeforeEach
    void provisionTwoTenantsWithAContract() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        acmeContract = TenantContext.callWith(acme.tenant().getId(), () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            Position position = orgStructure.createPosition(company, department, "Analista", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            Employee employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            return contracts.hire(employee.getId(), company.getId(), position.getId(), null, null,
                    ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                    new BigDecimal("40000.00"), "DOP");
        });
    }

    @Test
    @DisplayName("un contrato creado en un tenant no se ve consultando desde otro")
    void contractsDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> contractRepository.findById(acmeContract.getId())))
                .as("bajo Acme se resuelve el contrato recien creado")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> contractRepository.findById(acmeContract.getId())))
                .as("el mismo id consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("el histórico salarial de un contrato no se ve consultando desde otro tenant")
    void salaryHistoryDoesNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> salaryHistoryRepository.findByContractId(acmeContract.getId())))
                .as("bajo Acme se ve el registro salarial inicial")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> salaryHistoryRepository.findByContractId(acmeContract.getId())))
                .as("el mismo contract_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("el histórico de Envers (employment_contracts_aud + revinfo) no se ve desde otro tenant")
    void enversHistoryDoesNotLeakBetweenTenants() throws Exception {
        // Genera una segunda revisión para tener algo mas que la del alta.
        TenantContext.runWith(acme.tenant().getId(), () ->
                contracts.changeSalary(acmeContract.getId(), new BigDecimal("45000.00"), "DOP",
                        LocalDate.of(2024, 6, 1), SalaryChangeReason.RAISE));

        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ensureProbeRole(admin);
        }

        try (Connection probe = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "rls_probe", "probe")) {
            fixTenant(probe, acme.tenant().getId());
            long acmeRevisions = countWhere(probe,
                    "SELECT count(*) FROM employment_contracts_aud WHERE id = ?", acmeContract.getId());
            assertThat(acmeRevisions)
                    .as("bajo Acme se ven las revisiones del contrato recien creado")
                    .isPositive();

            fixTenant(probe, globex.tenant().getId());
            long globexRevisions = countWhere(probe,
                    "SELECT count(*) FROM employment_contracts_aud WHERE id = ?", acmeContract.getId());
            assertThat(globexRevisions)
                    .as("el mismo contract_id en employment_contracts_aud consultado bajo Globex no "
                            + "devuelve nada: el JOIN contra revinfo de V11 lo bloquea igual que una "
                            + "columna tenant_id directa")
                    .isZero();
        }
    }

    private static void ensureProbeRole(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rls_probe') THEN
                            CREATE ROLE rls_probe LOGIN PASSWORD 'probe';
                        END IF;
                    END $$
                    """);
            statement.execute("GRANT USAGE ON SCHEMA public TO rls_probe");
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO rls_probe");
        }
    }

    private static void fixTenant(Connection connection, Long tenantId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
        }
    }

    private static long countWhere(Connection connection, String sql, Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
