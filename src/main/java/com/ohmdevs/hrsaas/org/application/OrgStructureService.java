package com.ohmdevs.hrsaas.org.application;

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
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

/**
 * Alta y edición de la estructura organizacional del tenant activo.
 *
 * <p>Todos los métodos exigen {@link TenantContext} establecido, igual que
 * {@code RoleService} en {@code platform}: sin tenant, {@code @TenantId} no
 * tiene qué rellenar y la escritura debe fallar explícito, no en silencio.
 *
 * <p>Las comprobaciones de duplicado aquí son una respuesta más clara para
 * quien llama; la garantía real de unicidad la da la restricción
 * {@code UNIQUE} de la migración — esta capa no la reemplaza, la anticipa.
 *
 * <p>Los métodos {@code update*} nunca tocan {@code rnc}/{@code code}: son
 * la clave de negocio de cada entidad (única por tenant/empresa) y cambiarla
 * en caliente rompería referencias que ya la usan como identificador externo
 * (un centro de costo ya exportado a un ERP, una empresa ya declarada ante la
 * DGII). Quien necesite corregir un código mal capturado hoy da de baja el
 * registro y crea uno nuevo — no hay endpoint para eso todavía.
 */
@Service
public class OrgStructureService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final CostCenterRepository costCenterRepository;
    private final PayGradeRepository payGradeRepository;

    public OrgStructureService(CompanyRepository companyRepository,
                                LocationRepository locationRepository,
                                DepartmentRepository departmentRepository,
                                PositionRepository positionRepository,
                                CostCenterRepository costCenterRepository,
                                PayGradeRepository payGradeRepository) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.costCenterRepository = costCenterRepository;
        this.payGradeRepository = payGradeRepository;
    }

    @Transactional
    public Company createCompany(String legalName, String tradeName, String rnc) {
        TenantContext.require();
        companyRepository.findByRnc(rnc).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe una empresa con RNC " + rnc + " en este tenant");
        });
        Company company = new Company();
        company.setLegalName(legalName);
        company.setTradeName(tradeName);
        company.setRnc(rnc);
        return companyRepository.save(company);
    }

    @Transactional
    public Company updateCompany(Company company, String legalName, String tradeName, boolean active) {
        TenantContext.require();
        company.setLegalName(legalName);
        company.setTradeName(tradeName);
        company.setActive(active);
        return companyRepository.save(company);
    }

    @Transactional
    public Location createLocation(Company company, String name, String address) {
        TenantContext.require();
        Location location = new Location();
        location.setCompany(company);
        location.setName(name);
        location.setAddress(address);
        return locationRepository.save(location);
    }

    @Transactional
    public Location updateLocation(Location location, String name, String address, boolean active) {
        TenantContext.require();
        location.setName(name);
        location.setAddress(address);
        location.setActive(active);
        return locationRepository.save(location);
    }

    @Transactional
    public Department createDepartment(Company company, String name) {
        TenantContext.require();
        Department department = new Department();
        department.setCompany(company);
        department.setName(name);
        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(Department department, String name, boolean active) {
        TenantContext.require();
        department.setName(name);
        department.setActive(active);
        return departmentRepository.save(department);
    }

    @Transactional
    public Position createPosition(Company company, Department department, String title, PayGrade payGrade) {
        TenantContext.require();
        Position position = new Position();
        position.setCompany(company);
        position.setDepartment(department);
        position.setTitle(title);
        position.setPayGrade(payGrade);
        return positionRepository.save(position);
    }

    /**
     * No recibe {@link Department} nuevo a propósito: mover un cargo de
     * departamento es un cambio estructural distinto de editarlo, sin
     * endpoint todavía.
     *
     * <p>{@code position} llega detached (lo cargó {@code OrgAdminQueryService.getPosition}
     * en su propia transacción, ya cerrada), así que {@code positionRepository.save}
     * hace un {@code merge} — y el {@code merge} de una asociación sin
     * {@code cascade} no conserva la inicialización previa de {@code department}:
     * la copia gestionada resultante trae un proxy nuevo, sin inicializar.
     * Por eso se relee con {@code findByIdFetchDepartment} antes de devolver,
     * en la misma transacción, para que {@code OrgAdminController.toResponse}
     * (que corre después, ya sin sesión) no vuelva a toparse con el
     * {@code LazyInitializationException} que motivó el fetch-join de
     * {@code PositionRepository}.
     */
    @Transactional
    public Position updatePosition(Position position, String title, PayGrade payGrade, boolean active) {
        TenantContext.require();
        position.setTitle(title);
        position.setPayGrade(payGrade);
        position.setActive(active);
        Long positionId = positionRepository.save(position).getId();
        return positionRepository.findByIdFetchDepartment(positionId)
                .orElseThrow(() -> new NoSuchElementException("Cargo " + positionId + " no encontrado"));
    }

    @Transactional
    public CostCenter createCostCenter(Company company, String code, String name) {
        TenantContext.require();
        costCenterRepository.findByCompanyIdAndCode(company.getId(), code).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Ya existe un centro de costo '" + code + "' en esta empresa");
        });
        CostCenter costCenter = new CostCenter();
        costCenter.setCompany(company);
        costCenter.setCode(code);
        costCenter.setName(name);
        return costCenterRepository.save(costCenter);
    }

    @Transactional
    public CostCenter updateCostCenter(CostCenter costCenter, String name, boolean active) {
        TenantContext.require();
        costCenter.setName(name);
        costCenter.setActive(active);
        return costCenterRepository.save(costCenter);
    }

    @Transactional
    public PayGrade createPayGrade(Company company, String code, String name,
                                    BigDecimal minSalary, BigDecimal maxSalary) {
        TenantContext.require();
        payGradeRepository.findByCompanyIdAndCode(company.getId(), code).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Ya existe una banda salarial '" + code + "' en esta empresa");
        });
        PayGrade payGrade = new PayGrade();
        payGrade.setCompany(company);
        payGrade.setCode(code);
        payGrade.setName(name);
        payGrade.setMinSalary(minSalary);
        payGrade.setMaxSalary(maxSalary);
        return payGradeRepository.save(payGrade);
    }

    @Transactional
    public PayGrade updatePayGrade(PayGrade payGrade, String name, BigDecimal minSalary, BigDecimal maxSalary, boolean active) {
        TenantContext.require();
        if (maxSalary.compareTo(minSalary) < 0) {
            throw new IllegalArgumentException("El salario maximo no puede ser menor que el minimo");
        }
        payGrade.setName(name);
        payGrade.setMinSalary(minSalary);
        payGrade.setMaxSalary(maxSalary);
        payGrade.setActive(active);
        return payGradeRepository.save(payGrade);
    }
}
