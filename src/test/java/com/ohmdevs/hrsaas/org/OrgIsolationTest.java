package com.ohmdevs.hrsaas.org;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.CostCenter;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Location;
import com.ohmdevs.hrsaas.org.domain.PayGrade;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.org.infrastructure.CompanyRepository;
import com.ohmdevs.hrsaas.org.infrastructure.CostCenterRepository;
import com.ohmdevs.hrsaas.org.infrastructure.DepartmentRepository;
import com.ohmdevs.hrsaas.org.infrastructure.LocationRepository;
import com.ohmdevs.hrsaas.org.infrastructure.PayGradeRepository;
import com.ohmdevs.hrsaas.org.infrastructure.PositionRepository;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado del módulo {@code org}, mismo patrón que
 * {@code PlatformIsolationTest}: dos tenants sembrados, cada repositorio
 * verificado por separado — ninguno cruza la frontera.
 *
 * <p>Reutiliza {@link TenantProvisioningService} de {@code platform} solo
 * para tener dos tenants reales sobre los que colgar la estructura; no prueba
 * nada de {@code platform} aquí, eso ya lo cubre su propia suite.
 */
class OrgIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private CostCenterRepository costCenterRepository;
    @Autowired
    private PayGradeRepository payGradeRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private Long acmeCompanyId;

    @BeforeEach
    void provisionTwoTenantsWithOrgStructure() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        acmeCompanyId = TenantContext.callWith(acme.tenant().getId(), () ->
                orgStructure.createCompany("Acme Republica Dominicana SRL", "Acme RD", "101234567").getId());
    }

    @Test
    @DisplayName("una empresa creada en un tenant no existe para el otro")
    void companiesDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(), () -> companyRepository.findByRnc("101234567")))
                .as("bajo Acme se resuelve la empresa recien creada")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(), () -> companyRepository.findByRnc("101234567")))
                .as("el mismo RNC consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("una sucursal no se ve consultando desde otro tenant")
    void locationsDoNotLeakBetweenTenants() {
        Company acmeCompany = TenantContext.callWith(acme.tenant().getId(),
                () -> companyRepository.findById(acmeCompanyId).orElseThrow());
        Location created = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createLocation(acmeCompany, "Sede Piantini", "Av. Piantini 123"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> locationRepository.findByCompanyId(acmeCompanyId)))
                .as("bajo Acme se ve la sucursal recien creada")
                .extracting(Location::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> locationRepository.findByCompanyId(acmeCompanyId)))
                .as("el mismo company_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un departamento no se ve consultando desde otro tenant")
    void departmentsDoNotLeakBetweenTenants() {
        Company acmeCompany = TenantContext.callWith(acme.tenant().getId(),
                () -> companyRepository.findById(acmeCompanyId).orElseThrow());
        Department created = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createDepartment(acmeCompany, "Recursos Humanos"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> departmentRepository.findByCompanyId(acmeCompanyId)))
                .extracting(Department::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> departmentRepository.findByCompanyId(acmeCompanyId)))
                .as("el mismo company_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un cargo no se ve consultando desde otro tenant")
    void positionsDoNotLeakBetweenTenants() {
        Company acmeCompany = TenantContext.callWith(acme.tenant().getId(),
                () -> companyRepository.findById(acmeCompanyId).orElseThrow());
        Department department = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createDepartment(acmeCompany, "Tecnologia"));
        Position created = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createPosition(acmeCompany, department, "Analista de Nomina", null));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> positionRepository.findByDepartmentId(department.getId())))
                .extracting(Position::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> positionRepository.findByDepartmentId(department.getId())))
                .as("el mismo department_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un centro de costo no se ve consultando desde otro tenant")
    void costCentersDoNotLeakBetweenTenants() {
        Company acmeCompany = TenantContext.callWith(acme.tenant().getId(),
                () -> companyRepository.findById(acmeCompanyId).orElseThrow());
        CostCenter created = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createCostCenter(acmeCompany, "CC-01", "Administracion"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> costCenterRepository.findByCompanyIdAndCode(acmeCompanyId, "CC-01")))
                .as("bajo Acme se resuelve el centro de costo recien creado")
                .map(CostCenter::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> costCenterRepository.findByCompanyIdAndCode(acmeCompanyId, "CC-01")))
                .as("el mismo company_id/code consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("una banda salarial no se ve consultando desde otro tenant")
    void payGradesDoNotLeakBetweenTenants() {
        Company acmeCompany = TenantContext.callWith(acme.tenant().getId(),
                () -> companyRepository.findById(acmeCompanyId).orElseThrow());
        PayGrade created = TenantContext.callWith(acme.tenant().getId(),
                () -> orgStructure.createPayGrade(acmeCompany, "PG-01", "Analista",
                        new BigDecimal("30000.00"), new BigDecimal("45000.00")));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> payGradeRepository.findByCompanyIdAndCode(acmeCompanyId, "PG-01")))
                .as("bajo Acme se resuelve la banda salarial recien creada")
                .map(PayGrade::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> payGradeRepository.findByCompanyIdAndCode(acmeCompanyId, "PG-01")))
                .as("el mismo company_id/code consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
