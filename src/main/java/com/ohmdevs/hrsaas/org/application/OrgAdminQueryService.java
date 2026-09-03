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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lecturas de la estructura organizacional para {@code org.web.OrgAdminController}.
 * Separado de {@link OrgStructureService} a propósito: aquel es alta
 * (comandos), este es consulta — mismo criterio de separación que
 * {@code contracts.application.ContractLifecycleService} (comandos) frente a
 * {@code contracts.api.ContractPayrollQueryService} (consulta).
 *
 * <p>Ninguna colección de este servicio lleva paginación por cursor: todas
 * están acotadas a lo que existe dentro de una sola empresa (departamentos,
 * cargos, centros de costo, bandas salariales), que en la práctica son
 * decenas de filas, no miles — a diferencia de empleados y contratos, que sí
 * la necesitan.
 */
@Service
public class OrgAdminQueryService {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final PositionRepository positionRepository;
    private final CostCenterRepository costCenterRepository;
    private final PayGradeRepository payGradeRepository;

    public OrgAdminQueryService(CompanyRepository companyRepository,
                                 DepartmentRepository departmentRepository,
                                 LocationRepository locationRepository,
                                 PositionRepository positionRepository,
                                 CostCenterRepository costCenterRepository,
                                 PayGradeRepository payGradeRepository) {
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.positionRepository = positionRepository;
        this.costCenterRepository = costCenterRepository;
        this.payGradeRepository = payGradeRepository;
    }

    @Transactional
    public List<Company> listCompanies() {
        return companyRepository.findAll();
    }

    @Transactional
    public Company getCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NoSuchElementException("Empresa " + companyId + " no encontrada"));
    }

    /** Valida que la empresa exista en el tenant antes de listar, para no confundir "vacía" con "inexistente". */
    @Transactional
    public List<Department> listDepartments(Long companyId) {
        getCompany(companyId);
        return departmentRepository.findByCompanyId(companyId);
    }

    @Transactional
    public List<Location> listLocations(Long companyId) {
        getCompany(companyId);
        return locationRepository.findByCompanyId(companyId);
    }

    @Transactional
    public List<Position> listPositions(Long companyId) {
        getCompany(companyId);
        return positionRepository.findByCompanyId(companyId);
    }

    @Transactional
    public List<CostCenter> listCostCenters(Long companyId) {
        getCompany(companyId);
        return costCenterRepository.findByCompanyId(companyId);
    }

    @Transactional
    public List<PayGrade> listPayGrades(Long companyId) {
        getCompany(companyId);
        return payGradeRepository.findByCompanyId(companyId);
    }

    @Transactional
    public Department getDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NoSuchElementException("Departamento " + departmentId + " no encontrado"));
    }

    @Transactional
    public PayGrade getPayGrade(Long payGradeId) {
        return payGradeRepository.findById(payGradeId)
                .orElseThrow(() -> new NoSuchElementException("Banda salarial " + payGradeId + " no encontrada"));
    }
}
