package com.ohmdevs.hrsaas.dataio.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.dataio.domain.ImportBatch;
import com.ohmdevs.hrsaas.dataio.domain.ImportBatchStatus;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El test central de {@code dataio}: la tubería completa
 * (stage → validate → preview → commit) con {@code EmployeeImportRowHandler}
 * como único {@code ImportRowHandler} conectado, y la garantía de blueprint
 * §07 de que "los errores se acumulan por fila, no abortan el archivo."
 */
class DataImportServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private DataImportService dataImportService;
    @Autowired
    private EmployeeRepository employeeRepository;

    private Long tenantId;
    private String docA;
    private String docB;

    @BeforeEach
    void provisionTenant() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();
        docA = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
        docB = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
    }

    @Test
    @DisplayName("stage -> validate -> commit(VALID_ROWS_ONLY) con un CSV valido crea los empleados")
    void fullPipelineWithAllValidRowsCreatesEmployees() {
        String csv = header() + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000")
                + row("Luis", "Gomez", "1985-05-20", "CEDULA", docB, "luis@acme.test", "809-000-0001");

        ImportBatch staged = TenantContext.callWith(tenantId, () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));
        assertThat(staged.getStatus()).isEqualTo(ImportBatchStatus.STAGED);
        assertThat(staged.getTotalRows()).isEqualTo(2);

        ImportBatch validated = TenantContext.callWith(tenantId, () -> dataImportService.validate(staged.getId()));
        assertThat(validated.getStatus()).isEqualTo(ImportBatchStatus.VALIDATED);
        assertThat(validated.getValidRows()).isEqualTo(2);
        assertThat(validated.getErrorRows()).isZero();

        ImportBatch committed = TenantContext.callWith(tenantId,
                () -> dataImportService.commit(staged.getId(), CommitMode.VALID_ROWS_ONLY));
        assertThat(committed.getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);

        long employeeCount = TenantContext.callWith(tenantId, employeeRepository::count);
        assertThat(employeeCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("un CSV con filas invalidas acumula errores por fila, sin abortar el archivo completo")
    void errorsAccumulatePerRowWithoutAbortingTheWholeFile() {
        String csv = header()
                + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000") // valida
                + row("", "SinNombre", "1990-01-01", "CEDULA", docB, "x@acme.test", "809-000-0002")   // firstName vacio
                + row("Luis", "Gomez", "fecha-invalida", "CEDULA", "001-9999999-9", "luis@acme.test", "809-000-0003"); // fecha mala

        ImportBatch staged = TenantContext.callWith(tenantId, () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));
        assertThat(staged.getTotalRows()).isEqualTo(3);

        ImportBatch validated = TenantContext.callWith(tenantId, () -> dataImportService.validate(staged.getId()));
        assertThat(validated.getValidRows()).isEqualTo(1);
        assertThat(validated.getErrorRows()).isEqualTo(2);

        ImportPreview preview = TenantContext.callWith(tenantId, () -> dataImportService.preview(staged.getId()));
        assertThat(preview.totalRows()).isEqualTo(3);
        assertThat(preview.rowErrors()).hasSize(2);
        assertThat(preview.rowErrors())
                .as("las lineas 2 y 3 son las que fallaron (la 1 es la cabecera, ya descartada)")
                .extracting(ImportPreview.RowErrorSummary::lineNumber)
                .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    @DisplayName("subir el mismo archivo dos veces es rechazado por idempotencia de hash")
    void stagingTheSameFileTwiceIsRejected() {
        byte[] csvBytes = (header() + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000"))
                .getBytes(StandardCharsets.UTF_8);

        TenantContext.runWith(tenantId, () -> dataImportService.stage("EMPLOYEES", csvBytes, "empleados.csv", 1L));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> dataImportService.stage("EMPLOYEES", csvBytes, "empleados-otra-vez.csv", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue subido");
    }

    @Test
    @DisplayName("ALL_OR_NOTHING se niega a comitear si hay al menos una fila con error")
    void allOrNothingRefusesToCommitWhenThereAreErrors() {
        String csv = header()
                + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000")
                + row("", "SinNombre", "1990-01-01", "CEDULA", docB, "x@acme.test", "809-000-0002");

        ImportBatch staged = TenantContext.callWith(tenantId, () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));
        TenantContext.runWith(tenantId, () -> dataImportService.validate(staged.getId()));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> dataImportService.commit(staged.getId(), CommitMode.ALL_OR_NOTHING)))
                .isInstanceOf(IllegalStateException.class);

        long employeeCount = TenantContext.callWith(tenantId, employeeRepository::count);
        assertThat(employeeCount)
                .as("todo-o-nada no debe haber creado ningun empleado")
                .isZero();
    }

    @Test
    @DisplayName("VALID_ROWS_ONLY comitea solo las filas validas, ignorando las que fallaron")
    void validRowsOnlyCommitsJustTheValidRows() {
        String csv = header()
                + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000")
                + row("", "SinNombre", "1990-01-01", "CEDULA", docB, "x@acme.test", "809-000-0002");

        ImportBatch staged = TenantContext.callWith(tenantId, () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));
        TenantContext.runWith(tenantId, () -> dataImportService.validate(staged.getId()));
        TenantContext.runWith(tenantId, () -> dataImportService.commit(staged.getId(), CommitMode.VALID_ROWS_ONLY));

        long employeeCount = TenantContext.callWith(tenantId, employeeRepository::count);
        assertThat(employeeCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("comitear sin validar primero es una transicion ilegal")
    void committingBeforeValidatingIsRejected() {
        String csv = header() + row("Ana", "Perez", "1990-01-01", "CEDULA", docA, "ana@acme.test", "809-000-0000");
        ImportBatch staged = TenantContext.callWith(tenantId, () -> dataImportService.stage(
                "EMPLOYEES", csv.getBytes(StandardCharsets.UTF_8), "empleados.csv", 1L));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId,
                () -> dataImportService.commit(staged.getId(), CommitMode.VALID_ROWS_ONLY)))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    private static String header() {
        return "firstName,lastName,birthDate,documentType,documentNumber,email,phone\n";
    }

    private static String row(String firstName, String lastName, String birthDate, String documentType,
                               String documentNumber, String email, String phone) {
        return String.join(",", firstName, lastName, birthDate, documentType, documentNumber, email, phone) + "\n";
    }
}
