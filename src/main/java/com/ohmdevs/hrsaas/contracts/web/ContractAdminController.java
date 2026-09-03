package com.ohmdevs.hrsaas.contracts.web;

import com.ohmdevs.hrsaas.contracts.application.ContractAdminQueryService;
import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractAmendment;
import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.contracts.domain.Termination;
import com.ohmdevs.hrsaas.org.api.CompanyDirectorySummary;
import com.ohmdevs.hrsaas.org.api.OrgDirectoryQueryService;
import com.ohmdevs.hrsaas.org.api.PositionDirectorySummary;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectoryQueryService;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectorySummary;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.shared.web.CursorCodec;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import com.ohmdevs.hrsaas.shared.web.PageLimits;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API administrativa de contratos: alta ("contratar"), cambios de salario,
 * promoción, suspensión/reactivación y terminación — cada operación delega
 * en {@link ContractLifecycleService}, el único punto que valida
 * {@code ContractStatus.canTransitionTo} (AD-06). Este controlador no
 * duplica esa validación ni escribe directamente sobre la entidad.
 *
 * <p><strong>Alcance real de {@code COMPANY} en esta primera pasada</strong>
 * (mismo límite documentado en {@code people.web.EmployeeAdminController}):
 * se exige que el actor tenga {@code COMPANY} o más concedido, pero no existe
 * todavía una tabla de "empresas asignadas al actor" en {@code platform} para
 * restringir el filtro a solo esas — a diferencia de {@code people}, aquí
 * {@code EmploymentContract} sí tiene {@code companyId}, así que el filtro
 * {@code ?companyId=} en {@code GET /api/v1/contracts} es una consulta de
 * negocio legítima y funciona hoy; lo que falta es que el propio backend
 * decida automáticamente a qué empresas restringir a un actor sin que él lo
 * pida en la URL.
 *
 * <p>La consulta enriquece cada fila con nombre de empleado, empresa y cargo
 * (vía {@code people.api}/{@code org.api}) — una llamada extra por fila y por
 * módulo, aceptable al tamaño de página acotado por {@link PageLimits} (máx.
 * 50), pero un N+1 real si algún día una empresa piloto tiene miles de
 * contratos por página. Revisar si eso ocurre antes de necesitar un método de
 * búsqueda en lote en {@code people.api}/{@code org.api}.
 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractAdminController {

    private final ContractAdminQueryService queryService;
    private final ContractLifecycleService contractLifecycle;
    private final EmployeeDirectoryQueryService employeeDirectory;
    private final OrgDirectoryQueryService orgDirectory;
    private final AccessGuard accessGuard;

    public ContractAdminController(ContractAdminQueryService queryService, ContractLifecycleService contractLifecycle,
                                    EmployeeDirectoryQueryService employeeDirectory, OrgDirectoryQueryService orgDirectory,
                                    AccessGuard accessGuard) {
        this.queryService = queryService;
        this.contractLifecycle = contractLifecycle;
        this.employeeDirectory = employeeDirectory;
        this.orgDirectory = orgDirectory;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public CursorPage<ContractResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status) {
        accessGuard.require("contract:read", PermissionScope.COMPANY);

        ContractStatus statusFilter = (status == null || status.isBlank()) ? null : ContractStatus.valueOf(status.toUpperCase());
        int clampedLimit = PageLimits.clamp(limit);

        ContractAdminQueryService.ContractPage page = queryService.search(
                employeeId, companyId, statusFilter, CursorCodec.decode(cursor), clampedLimit);
        var items = page.items().stream().map(this::toResponse).toList();
        String nextCursor = page.nextAfterId() == null ? null : CursorCodec.encode(page.nextAfterId());
        return new CursorPage<>(items, nextCursor);
    }

    @GetMapping("/{id}")
    public ContractDetailResponse detail(@PathVariable Long id) {
        accessGuard.require("contract:read", PermissionScope.COMPANY);
        EmploymentContract contract = queryService.getById(id);

        List<SalaryHistoryResponse> salaryHistory = queryService.salaryHistory(id).stream()
                .map(ContractAdminController::toResponse).toList();
        List<ContractAmendmentResponse> amendments = queryService.amendments(id).stream()
                .map(ContractAdminController::toResponse).toList();
        TerminationResponse termination = queryService.termination(id).map(ContractAdminController::toResponse).orElse(null);

        return new ContractDetailResponse(toResponse(contract), salaryHistory, amendments, termination);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse hire(@Valid @RequestBody ContractCreateRequest request) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        EmploymentContract contract = contractLifecycle.hire(
                request.employeeId(), request.companyId(), request.positionId(), request.costCenterId(),
                request.supervisorEmployeeId(), request.contractType(), request.startDate(), request.endDate(),
                request.probationEndsAt(), request.initialSalary(), request.currency());
        return toResponse(contract);
    }

    @PostMapping("/{id}/salary-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryHistoryResponse changeSalary(@PathVariable Long id, @Valid @RequestBody SalaryChangeRequest request) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        SalaryHistory history = contractLifecycle.changeSalary(
                id, request.amount(), request.currency(), request.effectiveFrom(), request.reason());
        return toResponse(history);
    }

    @PostMapping("/{id}/promote")
    public ContractResponse promote(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        EmploymentContract contract = contractLifecycle.promote(id, request.newPositionId(), request.effectiveDate(), request.notes());
        return toResponse(contract);
    }

    @PostMapping("/{id}/suspend")
    public ContractResponse suspend(@PathVariable Long id) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        return toResponse(contractLifecycle.suspend(id));
    }

    @PostMapping("/{id}/reactivate")
    public ContractResponse reactivate(@PathVariable Long id) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        return toResponse(contractLifecycle.reactivate(id));
    }

    @PostMapping("/{id}/terminate")
    public TerminationResponse terminate(@PathVariable Long id, @Valid @RequestBody TerminationRequest request) {
        accessGuard.require("contract:manage", PermissionScope.COMPANY);
        Termination termination = contractLifecycle.terminate(
                id, request.terminationDate(), request.cause(), request.noticeGiven(),
                request.severanceEligible(), request.notes());
        return toResponse(termination);
    }

    private ContractResponse toResponse(EmploymentContract contract) {
        EmployeeDirectorySummary employee = employeeDirectory.findById(contract.getEmployeeId()).orElse(null);
        CompanyDirectorySummary company = orgDirectory.findCompany(contract.getCompanyId()).orElse(null);
        PositionDirectorySummary position = orgDirectory.findPosition(contract.getPositionId()).orElse(null);
        SalaryHistory openSalary = queryService.openSalary(contract.getId()).orElse(null);

        return new ContractResponse(
                contract.getId(), contract.getEmployeeId(),
                employee == null ? null : employee.fullName(),
                employee == null ? null : employee.maskedDocumentNumber(),
                contract.getCompanyId(),
                company == null ? null : (company.tradeName() != null ? company.tradeName() : company.legalName()),
                contract.getPositionId(), position == null ? null : position.title(),
                contract.getCostCenterId(), contract.getSupervisorEmployeeId(),
                contract.getContractType().name(), contract.getStartDate(), contract.getEndDate(),
                contract.getProbationEndsAt(), contract.getStatus().name(),
                openSalary == null ? null : openSalary.getAmount(), openSalary == null ? null : openSalary.getCurrency(),
                contract.getCreatedAt());
    }

    private static SalaryHistoryResponse toResponse(SalaryHistory history) {
        return new SalaryHistoryResponse(history.getId(), history.getAmount(), history.getCurrency(),
                history.getEffectiveFrom(), history.getEffectiveTo(), history.getReason().name());
    }

    private static ContractAmendmentResponse toResponse(ContractAmendment amendment) {
        return new ContractAmendmentResponse(amendment.getId(), amendment.getAmendmentType().name(),
                amendment.getEffectiveDate(), amendment.getDetail(), amendment.getNotes());
    }

    private static TerminationResponse toResponse(Termination termination) {
        return new TerminationResponse(termination.getId(), termination.getTerminationDate(),
                termination.getCause().name(), termination.isNoticeGiven(), termination.isSeveranceEligible(),
                termination.getNotes());
    }
}
