package com.ohmdevs.hrsaas.dataio;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.dataio.application.DataImportService;
import com.ohmdevs.hrsaas.dataio.domain.ImportBatch;
import com.ohmdevs.hrsaas.dataio.infrastructure.ImportBatchRepository;
import com.ohmdevs.hrsaas.dataio.infrastructure.ImportRowRepository;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aislamiento cruzado de {@code dataio} (AD-02) — mismo patrón que el resto de la suite. */
class DataioIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private DataImportService dataImportService;
    @Autowired
    private ImportBatchRepository importBatchRepository;
    @Autowired
    private ImportRowRepository importRowRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;
    private ImportBatch acmeBatch;

    @BeforeEach
    void provisionTwoTenantsWithAStagedBatchForAcme() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");

        String csv = "firstName,lastName,birthDate,documentType,documentNumber,email,phone\n"
                + "Ana,Perez,1990-01-01,CEDULA,001-" + UUID.randomUUID().toString().substring(0, 7) + "-8,ana@acme.test,809-000-0000\n";

        acmeBatch = TenantContext.callWith(acme.tenant().getId(), () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));
    }

    @Test
    @DisplayName("un lote de importacion creado en un tenant no se ve consultando desde otro")
    void importBatchesDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(), () -> importBatchRepository.findById(acmeBatch.getId())))
                .as("bajo Acme se resuelve el lote recien creado")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(), () -> importBatchRepository.findById(acmeBatch.getId())))
                .as("el mismo id consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("las filas de un lote no se ven consultando desde otro tenant")
    void importRowsDoNotLeakBetweenTenants() {
        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> importRowRepository.findByImportBatchIdOrderByLineNumberAsc(acmeBatch.getId())))
                .as("bajo Acme se ve la fila del lote")
                .isNotEmpty();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> importRowRepository.findByImportBatchIdOrderByLineNumberAsc(acmeBatch.getId())))
                .as("el mismo import_batch_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }
}
