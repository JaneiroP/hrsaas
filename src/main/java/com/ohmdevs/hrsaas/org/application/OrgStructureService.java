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

/**
 * Alta de la estructura organizacional del tenant activo.
 *
 * <p>Todos los métodos exigen {@link TenantContext} establecido, igual que
 * {@code RoleService} en {@code platform}: sin tenant, {@code @TenantId} no
 * tiene qué rellenar y la escritura debe fallar explícito, no en silencio.
 *
 * <p>Las comprobaciones de duplicado aquí son una respuesta más clara para
 * quien llama; la garantía real de unicidad la da la restricción
 * {@code UNIQUE} de la migración — esta capa no la reemplaza, la anticipa.
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
    public Location createLocation(Company company, String name, String address) {
        TenantContext.require();
        Location location = new Location();
        location.setCompany(company);
        location.setName(name);
        location.setAddress(address);
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
    public Position createPosition(Company company, Department department, String title, PayGrade payGrade) {
        TenantContext.require();
        Position position = new Position();
        position.setCompany(company);
        position.setDepartment(department);
        position.setTitle(title);
        position.setPayGrade(payGrade);
        return positionRepository.save(position);
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
}
