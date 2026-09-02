package com.ohmdevs.hrsaas.org.api;

import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.org.infrastructure.CompanyRepository;
import com.ohmdevs.hrsaas.org.infrastructure.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Primer contenido público de {@code org}: resuelve nombres por id para
 * {@code portal} (perfil del empleado, blueprint §09 — {@code GET /me}).
 * Devuelve proyecciones aplanadas, nunca las entidades de {@code org.domain},
 * mismo patrón que {@code contracts.api}/{@code people.api} con sus
 * {@code *Summary}.
 */
@Service
public class OrgDirectoryQueryService {

    private final CompanyRepository companyRepository;
    private final PositionRepository positionRepository;

    public OrgDirectoryQueryService(CompanyRepository companyRepository, PositionRepository positionRepository) {
        this.companyRepository = companyRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional
    public Optional<CompanyDirectorySummary> findCompany(Long companyId) {
        return companyRepository.findById(companyId).map(OrgDirectoryQueryService::toSummary);
    }

    @Transactional
    public Optional<PositionDirectorySummary> findPosition(Long positionId) {
        return positionRepository.findById(positionId).map(OrgDirectoryQueryService::toSummary);
    }

    private static CompanyDirectorySummary toSummary(Company company) {
        return new CompanyDirectorySummary(company.getId(), company.getLegalName(), company.getTradeName(), company.getRnc());
    }

    private static PositionDirectorySummary toSummary(Position position) {
        return new PositionDirectorySummary(
                position.getId(), position.getTitle(), position.getDepartment().getId(), position.getDepartment().getName());
    }
}
