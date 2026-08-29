package com.ohmdevs.hrsaas.assets;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.assets.application.AssetManagementService;
import com.ohmdevs.hrsaas.assets.domain.Asset;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetRepository;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aislamiento cruzado del módulo {@code assets}, mismo patrón que el resto de la suite. */
class AssetsIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private AssetManagementService assetManagement;
    @Autowired
    private AssetRepository assetRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;

    @BeforeEach
    void provisionTwoTenants() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");
    }

    @Test
    @DisplayName("un activo no se ve consultando desde otro tenant, ni por su etiqueta")
    void assetsDoNotLeakBetweenTenants() {
        Asset created = TenantContext.callWith(acme.tenant().getId(), () -> {
            Company company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            return assetManagement.registerAsset(company.getId(), "LAPTOP-001", "Laptop Dell", "Computo");
        });

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> assetRepository.findByAssetTag("LAPTOP-001")))
                .as("bajo Acme se resuelve el activo recien creado")
                .map(Asset::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> assetRepository.findByAssetTag("LAPTOP-001")))
                .as("la misma etiqueta consultada bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
