package com.ohmdevs.hrsaas.contracts.application;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryChangeReason;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.contracts.domain.Termination;
import com.ohmdevs.hrsaas.contracts.domain.TerminationCause;
import com.ohmdevs.hrsaas.contracts.infrastructure.SalaryHistoryRepository;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeStatus;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El test central de "terminado cuando" (AD-07): contratar, cambiar salario
 * dos veces en fechas distintas, promover y desvincular, y reconstruir con
 * {@link AuditReader} el cargo vigente en una revisión intermedia —
 * {@code EmploymentContract.positionId} se sobreescribe en el sitio (no hay
 * una tabla de historial de cargos separada), así que solo Envers puede
 * responder "qué cargo tenía este contrato antes de la última promoción".
 *
 * <p>El histórico salarial, en cambio, no necesita {@link AuditReader}:
 * {@link ContractLifecycleService#changeSalary} nunca edita una fila de
 * {@link SalaryHistory}, siempre cierra la abierta y abre una nueva — la
 * fecha vigente en cualquier momento se reconstruye con una consulta normal.
 *
 * <p>Las llamadas a {@link AuditReader} van envueltas en
 * {@link TransactionTemplate} en vez de {@code @Transactional} en un método
 * de esta clase: una llamada a {@code this.metodo()} dentro de la propia
 * clase de test no pasa por el proxy de Spring, así que
 * {@code @Transactional} en un método propio del test no tendría efecto
 * (auto-invocación, limitación conocida de Spring AOP).
 */
class ContractLifecycleServiceTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private ContractLifecycleService contracts;
    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long tenantId;
    private Company company;
    private Position positionAnalyst;
    private Position positionSeniorAnalyst;
    private Employee employee;

    @BeforeEach
    void provisionTenantAndOrgStructure() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        tenantId = acme.tenant().getId();

        TenantContext.runWith(tenantId, () -> {
            company = orgStructure.createCompany("Acme RD", "Acme", "101234567");
            Department department = orgStructure.createDepartment(company, "Tecnologia");
            positionAnalyst = orgStructure.createPosition(company, department, "Analista", null);
            positionSeniorAnalyst = orgStructure.createPosition(company, department, "Analista Senior", null);
            String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
            employee = employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                    DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000");
        });
    }

    @Test
    @DisplayName("hire -> dos cambios de salario -> promocion -> terminate reconstruye correctamente el historial")
    void fullLifecycleReconstructsCorrectlyWithAuditReader() {
        EmploymentContract contract = TenantContext.callWith(tenantId, () -> contracts.hire(
                employee.getId(), company.getId(), positionAnalyst.getId(), null, null,
                ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                new BigDecimal("40000.00"), "DOP"));

        assertThat(TenantContext.callWith(tenantId, () -> employeeRepository.findById(employee.getId())
                        .orElseThrow().getStatus()))
                .as("hire marca el expediente como ACTIVE via people.api.EmployeeLifecycleService")
                .isEqualTo(EmployeeStatus.ACTIVE);

        TenantContext.runWith(tenantId, () -> contracts.changeSalary(
                contract.getId(), new BigDecimal("45000.00"), "DOP",
                LocalDate.of(2024, 3, 1), SalaryChangeReason.RAISE));

        TenantContext.runWith(tenantId, () -> contracts.promote(
                contract.getId(), positionSeniorAnalyst.getId(), LocalDate.of(2024, 4, 1), "Ascenso por desempeño"));

        TenantContext.runWith(tenantId, () -> contracts.changeSalary(
                contract.getId(), new BigDecimal("52000.00"), "DOP",
                LocalDate.of(2024, 6, 1), SalaryChangeReason.PROMOTION));

        Termination termination = TenantContext.callWith(tenantId, () -> contracts.terminate(
                contract.getId(), LocalDate.of(2024, 12, 31), TerminationCause.RESIGNATION,
                true, true, "Renuncia voluntaria"));

        // --- Histórico salarial: consulta normal, sin AuditReader ---
        List<SalaryHistory> salaryHistory = TenantContext.callWith(tenantId,
                () -> salaryHistoryRepository.findByContractId(contract.getId()));
        assertThat(salaryHistory).hasSize(3);
        SalaryHistory salaryAsOfMay = salaryHistory.stream()
                .filter(entry -> !entry.getEffectiveFrom().isAfter(LocalDate.of(2024, 5, 1))
                        && (entry.getEffectiveTo() == null || !entry.getEffectiveTo().isBefore(LocalDate.of(2024, 5, 1))))
                .findFirst().orElseThrow();
        assertThat(salaryAsOfMay.getAmount()).isEqualByComparingTo("45000.00");

        // --- Cargo vigente en una revisión intermedia: solo AuditReader puede responderlo ---
        List<Number> revisions = TenantContext.callWith(tenantId, () -> readContractRevisions(contract.getId()));
        assertThat(revisions)
                .as("hire + promote + terminate deben dejar al menos 3 revisiones")
                .hasSizeGreaterThanOrEqualTo(3);

        // La revisión justo después del alta (índice 0): todavía el cargo original.
        EmploymentContract afterHire = TenantContext.callWith(tenantId,
                () -> findContractAtRevision(contract.getId(), revisions.get(0)));
        assertThat(afterHire.getPositionId()).isEqualTo(positionAnalyst.getId());
        assertThat(afterHire.getStatus()).isEqualTo(ContractStatus.ACTIVE);

        // La última revisión: contrato terminado.
        Number lastRevision = revisions.get(revisions.size() - 1);
        EmploymentContract finalState = TenantContext.callWith(tenantId,
                () -> findContractAtRevision(contract.getId(), lastRevision));
        assertThat(finalState.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(finalState.getPositionId())
                .as("el cargo promovido debe seguir vigente hasta el final: la promoción no se revierte")
                .isEqualTo(positionSeniorAnalyst.getId());

        assertThat(termination.getCause()).isEqualTo(TerminationCause.RESIGNATION);
        assertThat(TenantContext.callWith(tenantId, () -> employeeRepository.findById(employee.getId())
                        .orElseThrow().getStatus()))
                .as("terminate marca el expediente como INACTIVE via people.api.EmployeeLifecycleService")
                .isEqualTo(EmployeeStatus.INACTIVE);
    }

    @Test
    @DisplayName("una transicion ilegal de estado lanza IllegalStateTransitionException")
    void illegalTransitionIsRejected() {
        EmploymentContract contract = TenantContext.callWith(tenantId, () -> contracts.hire(
                employee.getId(), company.getId(), positionAnalyst.getId(), null, null,
                ContractType.INDEFINITE, LocalDate.of(2024, 1, 1), null, null,
                new BigDecimal("40000.00"), "DOP"));

        TenantContext.runWith(tenantId, () -> contracts.terminate(
                contract.getId(), LocalDate.of(2024, 6, 1), TerminationCause.RESIGNATION, true, true, null));

        assertThatThrownBy(() -> TenantContext.runWith(tenantId, () -> contracts.suspend(contract.getId())))
                .as("TERMINATED es un estado final: no puede volver a SUSPENDED")
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    private List<Number> readContractRevisions(Long contractId) {
        return transactionTemplate.execute(status ->
                AuditReaderFactory.get(entityManager).getRevisions(EmploymentContract.class, contractId));
    }

    private EmploymentContract findContractAtRevision(Long contractId, Number revision) {
        return transactionTemplate.execute(status ->
                AuditReaderFactory.get(entityManager).find(EmploymentContract.class, contractId, revision));
    }
}
