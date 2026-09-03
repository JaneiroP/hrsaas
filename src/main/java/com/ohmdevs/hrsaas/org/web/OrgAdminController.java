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
import org.springframework.web.bind.annotation.PatchMapping;
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
 * <p>{@code PATCH} edita nombre/campos mutables y el flag {@code active} de
 * cada entidad, nunca su clave de negocio ({@code rnc}, {@code code}) — ver
 * Javadoc de {@code OrgStructureService}. No hay {@code DELETE}: "de baja" es
 * {@code active=false}, nunca borrado físico — otros módulos (contratos,
 * nómina) referencian estas filas por id y borrarlas rompería su histórico.
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

    @PatchMapping("/companies/{companyId}")
    public CompanyResponse updateCompany(@PathVariable Long companyId, @Valid @RequestBody CompanyUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Company company = queries.getCompany(companyId);
        return toResponse(orgStructureService.updateCompany(company, request.legalName(), request.tradeName(), request.active()));
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

    @PatchMapping("/companies/{companyId}/departments/{id}")
    public DepartmentResponse updateDepartment(@PathVariable Long companyId, @PathVariable Long id,
                                                @Valid @RequestBody DepartmentUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Department department = requireOwnedByCompany(queries.getDepartment(id), companyId,
                "El departamento " + id + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.updateDepartment(department, request.name(), request.active()));
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

    @PatchMapping("/companies/{companyId}/locations/{id}")
    public LocationResponse updateLocation(@PathVariable Long companyId, @PathVariable Long id,
                                            @Valid @RequestBody LocationUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Location location = requireOwnedByCompany(queries.getLocation(id), companyId,
                "La sucursal " + id + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.updateLocation(location, request.name(), request.address(), request.active()));
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

    @PatchMapping("/companies/{companyId}/positions/{id}")
    public PositionResponse updatePosition(@PathVariable Long companyId, @PathVariable Long id,
                                            @Valid @RequestBody PositionUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        Position position = requireOwnedByCompany(queries.getPosition(id), companyId,
                "El cargo " + id + " no pertenece a la empresa " + companyId);
        PayGrade payGrade = request.payGradeId() == null ? null : requireOwnedByCompany(
                queries.getPayGrade(request.payGradeId()), companyId,
                "La banda salarial " + request.payGradeId() + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.updatePosition(position, request.title(), payGrade, request.active()));
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

    @PatchMapping("/companies/{companyId}/cost-centers/{id}")
    public CostCenterResponse updateCostCenter(@PathVariable Long companyId, @PathVariable Long id,
                                                @Valid @RequestBody CostCenterUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        CostCenter costCenter = requireOwnedByCompany(queries.getCostCenter(id), companyId,
                "El centro de costo " + id + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.updateCostCenter(costCenter, request.name(), request.active()));
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

    @PatchMapping("/companies/{companyId}/pay-grades/{id}")
    public PayGradeResponse updatePayGrade(@PathVariable Long companyId, @PathVariable Long id,
                                            @Valid @RequestBody PayGradeUpdateRequest request) {
        accessGuard.require("company:manage", PermissionScope.ALL);
        PayGrade payGrade = requireOwnedByCompany(queries.getPayGrade(id), companyId,
                "La banda salarial " + id + " no pertenece a la empresa " + companyId);
        return toResponse(orgStructureService.updatePayGrade(
                payGrade, request.name(), request.minSalary(), request.maxSalary(), request.active()));
    }

    /** IDs sueltos en el cuerpo o en la URL: nada impide que apunten a otra empresa del mismo tenant sin esta comprobación explícita. */
    private static <T> T requireOwnedByCompany(T entity, Long companyId, String message) {
        Long ownerCompanyId = switch (entity) {
            case Department d -> d.getCompany().getId();
            case Location l -> l.getCompany().getId();
            case Position p -> p.getCompany().getId();
            case CostCenter c -> c.getCompany().getId();
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
