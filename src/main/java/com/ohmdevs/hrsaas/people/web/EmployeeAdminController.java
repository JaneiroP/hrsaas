package com.ohmdevs.hrsaas.people.web;

import com.ohmdevs.hrsaas.people.application.DocumentNumberMasking;
import com.ohmdevs.hrsaas.people.application.EmployeeAdminQueryService;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeStatus;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.shared.web.CursorCodec;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import com.ohmdevs.hrsaas.shared.web.PageLimits;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API administrativa de expedientes de empleado (blueprint §12 Fase 5,
 * ampliación sobre el portal de autoservicio).
 *
 * <p><strong>Límite real de esta primera pasada, para no dar por hecho más de
 * lo que hay:</strong> {@code employee:read}/{@code employee:manage} se
 * exigen con un mínimo de {@link PermissionScope#COMPANY}, pero la fila de
 * {@code Employee} <em>no se filtra</em> por empresa — a propósito no lleva
 * {@code company_id} (ver Javadoc de {@link Employee}: "la persona no
 * pertenece a una razón social, un contrato sí"). Hoy tampoco existe una
 * tabla de "empresas asignadas al actor" en {@code platform} para resolver
 * ese filtro aunque quisiéramos. En la práctica, cualquier actor con
 * {@code COMPANY} o {@code ALL} concedido ve el expediente de cualquier
 * empleado del tenant. Quien necesite de verdad acotar por empresa hoy debe
 * hacerlo a través de {@code GET /api/v1/contracts?companyId=}, que sí tiene
 * esa columna — pendiente documentado, no una fuga silenciosa.
 *
 * <p>Sin endpoint de baja/reactivación aquí: el estado del expediente lo
 * decide la existencia de un contrato ({@code EmployeeLifecycleService}, ya
 * disparado por {@code ContractLifecycleService.hire}/{@code .terminate}) —
 * exponer un {@code PATCH /status} en este controlador permitiría dejar a un
 * empleado {@code ACTIVE} sin contrato o {@code INACTIVE} con uno vigente,
 * exactamente la inconsistencia que ese diseño evita.
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeAdminController {

    private final EmployeeAdminQueryService queryService;
    private final EmployeeRecordService employeeRecordService;
    private final AccessGuard accessGuard;

    public EmployeeAdminController(EmployeeAdminQueryService queryService, EmployeeRecordService employeeRecordService,
                                    AccessGuard accessGuard) {
        this.queryService = queryService;
        this.employeeRecordService = employeeRecordService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public CursorPage<EmployeeResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        accessGuard.require("employee:read", PermissionScope.COMPANY);

        EmployeeStatus statusFilter = (status == null || status.isBlank()) ? null : EmployeeStatus.valueOf(status.toUpperCase());
        String queryFilter = (q == null || q.isBlank()) ? null : q.trim();
        int clampedLimit = PageLimits.clamp(limit);

        EmployeeAdminQueryService.EmployeePage page = queryService.search(
                statusFilter, queryFilter, CursorCodec.decode(cursor), clampedLimit);
        var items = page.items().stream().map(EmployeeAdminController::toResponse).toList();
        String nextCursor = page.nextAfterId() == null ? null : CursorCodec.encode(page.nextAfterId());
        return new CursorPage<>(items, nextCursor);
    }

    @GetMapping("/{id}")
    public EmployeeResponse detail(@PathVariable Long id) {
        accessGuard.require("employee:read", PermissionScope.COMPANY);
        return toResponse(queryService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request) {
        accessGuard.require("employee:manage", PermissionScope.COMPANY);
        Employee employee = employeeRecordService.createEmployee(
                request.firstName(), request.lastName(), request.birthDate(), request.documentType(),
                request.documentNumber(), request.email(), request.phone());
        return toResponse(employee);
    }

    @PatchMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        accessGuard.require("employee:manage", PermissionScope.COMPANY);
        Employee employee = queryService.getById(id);
        employee = employeeRecordService.updateEmployee(
                employee, request.firstName(), request.lastName(), request.email(), request.phone());
        return toResponse(employee);
    }

    private static EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(), employee.getFirstName(), employee.getLastName(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getEmail(), employee.getPhone(), employee.getBirthDate(),
                employee.getDocumentType().name(),
                DocumentNumberMasking.mask(employee.getDocumentType(), employee.getDocumentNumber()),
                employee.getStatus().name(), employee.getCreatedAt());
    }
}
