package com.ohmdevs.hrsaas.assets.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.assets.domain.Asset;
import com.ohmdevs.hrsaas.assets.domain.AssetAssignment;
import com.ohmdevs.hrsaas.assets.domain.AssetAssignmentStatus;
import com.ohmdevs.hrsaas.assets.domain.AssetReturn;
import com.ohmdevs.hrsaas.assets.domain.AssetReturnCondition;
import com.ohmdevs.hrsaas.assets.domain.AssetStatus;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetAssignmentRepository;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetRepository;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetManagementServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private AssetManagementService assetManagement;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    private Long tenantId;
    private Employee employee;
    private Asset laptop;

    @BeforeEach
    void provisionTenantEmployeeAndAsset() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        TenantContext.runWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
            laptop = assetManagement.registerAsset(company.getId(), "LAPTOP-001", "Laptop Dell", "Computo");
        });
    }

    @Test
    @DisplayName("asignar marca el equipo ASSIGNED; devolverlo lo regresa a AVAILABLE y cierra la asignacion")
    void assignAndReturnFullCycle() {
        AssetAssignment assignment = TenantContext.callWith(tenantId, () ->
                assetManagement.assign(laptop.getId(), employee.getId(), "s3://acta-001.pdf"));

        Asset assignedAsset = TenantContext.callWith(tenantId, () -> assetRepository.findById(laptop.getId()).orElseThrow());
        assertThat(assignedAsset.getStatus()).isEqualTo(AssetStatus.ASSIGNED);

        assertThat(TenantContext.callWith(tenantId, () ->
                        assetAssignmentRepository.findByEmployeeIdAndStatus(employee.getId(), AssetAssignmentStatus.ACTIVE)))
                .as("checklist de devolucion: el empleado tiene un equipo activo asignado")
                .hasSize(1);

        AssetReturn assetReturn = TenantContext.callWith(tenantId, () ->
                assetManagement.returnAsset(assignment.getId(), AssetReturnCondition.GOOD, "Sin novedad"));
        assertThat(assetReturn.getCondition()).isEqualTo(AssetReturnCondition.GOOD);

        Asset returnedAsset = TenantContext.callWith(tenantId, () -> assetRepository.findById(laptop.getId()).orElseThrow());
        assertThat(returnedAsset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);

        assertThat(TenantContext.callWith(tenantId, () ->
                        assetAssignmentRepository.findByEmployeeIdAndStatus(employee.getId(), AssetAssignmentStatus.ACTIVE)))
                .as("tras la devolucion, el checklist ya no muestra equipos pendientes")
                .isEmpty();
    }

    @Test
    @DisplayName("asignar un equipo ya asignado lanza IllegalStateTransitionException")
    void assigningAnAlreadyAssignedAssetIsIllegal() {
        TenantContext.runWith(tenantId, () -> assetManagement.assign(laptop.getId(), employee.getId(), null));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> assetManagement.assign(laptop.getId(), employee.getId(), null)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
