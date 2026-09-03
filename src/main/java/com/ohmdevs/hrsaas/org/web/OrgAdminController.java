package com.ohmdevs.hrsaas.org.web;

import com.ohmdevs.hrsaas.org.application.OrgAdminQueryService;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.CostCenter;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Location;
import com.ohmdevs.hrsaas.org.domain.PayGrade;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API administrativa de estructura organizacional: razones sociales,
 * sucursales, departamentos, cargos, centros de costo y bandas salariales.
 *
 * <p>{@code company:read}/{@code company:manage} solo admiten alcance {@code ALL}
 * en el catálogo (blueprint §10, {@code V3__platform_permission_catalog.sql}):
 * a diferencia de {@code employee:*}/{@code contract:*}, aquí no existe un
 * alcance {@code COMPANY} intermedio con sentido — la propia colección que se
 * lista <strong>es</strong> el conjunto de empresas, así que el alcance mínimo
 * exigible es el tenant completo.
 *
 * <p>Sin endpoints de edición/baja para ninguna entidad de {@code org} en esta
 * primera pasada (solo alta y consulta) — {@code OrgStructureService} tampoco
 * los tiene todavía. Editar o desactivar una empresa/departamento/cargo queda
 * pendiente para una iteración futura; hoy un error de captura se corrige
 * dando de baja el registro a mano en base de datos o, más razonable,
 * ampliando {@code OrgStructureService} cuando el negocio lo pida.
 */
@RestController
@RequestMapping("/api/v1/org")
public class OrgAdminController {

    private final OrgAdminQueryService queries;
    private final OrgStructureService orgStructureService;
    private final AccessGuard accessGuard;

    public OrgAdminController(OrgAdminQueryService queries, OrgStructureService orgStructureService,
                               AccessGuard accessGuard) {
        this.queries = queries;
        this.orgStructureService = orgStructureService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/companies")
    public List<CompanyResponse> listCompanies() {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listCompanies().stream().map(OrgAdminController::toResponse).toList();
    }

    @GetMapping("/companies/{companyId}")
    public CompanyResponse getCompany(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return toResponse(queries.getCompany(companyId));
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(@Valid @RequestBody CompanyCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = orgStructureService.createCompany(request.legalName(), request.tradeName(), request.rnc());
        return toResponse(company);
    }

    @GetMapping("/companies/{companyId}/departments")
    public List<DepartmentResponse> listDepartments(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listDepartments(companyId).stream().map(OrgAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse createDepartment(@PathVariable Long companyId, @Valid @RequestBody DepartmentCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        return toResponse(orgStructureService.createDepartment(company, request.name()));
    }

    @GetMapping("/companies/{companyId}/locations")
    public List<LocationResponse> listLocations(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listLocations(companyId).stream().map(OrgAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse createLocation(@PathVariable Long companyId, @Valid @RequestBody LocationCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        return toResponse(orgStructureService.createLocation(company, request.name(), request.address()));
    }

    @GetMapping("/companies/{companyId}/positions")
    public List<PositionResponse> listPositions(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listPositions(companyId).stream().map(OrgAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse createPosition(@PathVariable Long companyId, @Valid @RequestBody PositionCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        Department department = requireOwnedByCompany(queries.getDepartment(request.departmentId()), companyId,
                "El departamento " + request.departmentId() + " no pertenece a la empresa " + companyId);
        PayGrade payGrade = request.payGradeId() == null ? null : requireOwnedByCompany(
                queries.getPayGrade(request.payGradeId()), companyId,
                "La banda salarial " + request.payGradeId() + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.createPosition(company, department, request.title(), payGrade));
    }

    @GetMapping("/companies/{companyId}/cost-centers")
    public List<CostCenterResponse> listCostCenters(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listCostCenters(companyId).stream().map(OrgAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/cost-centers")
    @ResponseStatus(HttpStatus.CREATED)
    public CostCenterResponse createCostCenter(@PathVariable Long companyId, @Valid @RequestBody CostCenterCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        return toResponse(orgStructureService.createCostCenter(company, request.code(), request.name()));
    }

    @GetMapping("/companies/{companyId}/pay-grades")
    public List<PayGradeResponse> listPayGrades(@PathVariable Long companyId) {
        accessGuard.require("company:read", PermissionScope.ALL);
        return queries.listPayGrades(companyId).stream().map(OrgAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/pay-grades")
    @ResponseStatus(HttpStatus.CREATED)
    public PayGradeResponse createPayGrade(@PathVariable Long companyId, @Valid @RequestBody PayGradeCreateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        return toResponse(orgStructureService.createPayGrade(
                company, request.code(), request.name(), request.minSalary(), request.maxSalary()));
    }

    /** {@code department}/{@code payGrade} llegan por id suelto en el cuerpo: nada impide que apunten a otra empresa del mismo tenant sin esta comprobación explícita. */
    private static <T> T requireOwnedByCompany(T entity, Long companyId, String message) {
        Long ownerCompanyId = switch (entity) {
            case Department d -> d.getCompany().getId();
            case PayGrade p -> p.getCompany().getId();
            default -> throw new IllegalStateException("Tipo no soportado: " + entity.getClass());
        };
        if (!ownerCompanyId.equals(companyId)) {
            throw new IllegalArgumentException(message);
        }
        return entity;
    }

    private static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(company.getId(), company.getLegalName(), company.getTradeName(),
                company.getRnc(), company.isActive());
    }

    private static DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getCompany().getId(),
                department.getName(), department.isActive());
    }

    private static LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getCompany().getId(),
                location.getName(), location.getAddress(), location.isActive());
    }

    private static PositionResponse toResponse(Position position) {
        return new PositionResponse(position.getId(), position.getCompany().getId(),
                position.getDepartment().getId(), position.getDepartment().getName(),
                position.getTitle(), position.getPayGrade() == null ? null : position.getPayGrade().getId(),
                position.isActive());
    }

    private static CostCenterResponse toResponse(CostCenter costCenter) {
        return new CostCenterResponse(costCenter.getId(), costCenter.getCompany().getId(),
                costCenter.getCode(), costCenter.getName(), costCenter.isActive());
    }

    private static PayGradeResponse toResponse(PayGrade payGrade) {
        return new PayGradeResponse(payGrade.getId(), payGrade.getCompany().getId(), payGrade.getCode(),
                payGrade.getName(), payGrade.getMinSalary(), payGrade.getMaxSalary(),
                payGrade.getCurrency(), payGrade.isActive());
    }
}
