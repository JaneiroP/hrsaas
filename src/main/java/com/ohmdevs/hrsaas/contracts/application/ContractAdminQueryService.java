package com.ohmdevs.hrsaas.contracts.application;

import com.ohmdevs.hrsaas.contracts.domain.ContractAmendment;
import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.contracts.domain.Termination;
import com.ohmdevs.hrsaas.contracts.infrastructure.ContractAmendmentRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.EmploymentContractRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.SalaryHistoryRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.TerminationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Lecturas administrativas de {@code contracts} para {@code contracts.web.ContractAdminController}.
 * Mismo criterio de separación que {@code org.application.OrgAdminQueryService}
 * y {@code people.application.EmployeeAdminQueryService}: {@link ContractLifecycleService}
 * es comando, este es consulta.
 */
@Service
public class ContractAdminQueryService {

    private final EmploymentContractRepository contractRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final ContractAmendmentRepository amendmentRepository;
    private final TerminationRepository terminationRepository;

    public ContractAdminQueryService(EmploymentContractRepository contractRepository,
                                      SalaryHistoryRepository salaryHistoryRepository,
                                      ContractAmendmentRepository amendmentRepository,
                                      TerminationRepository terminationRepository) {
        this.contractRepository = contractRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.amendmentRepository = amendmentRepository;
        this.terminationRepository = terminationRepository;
    }

    /** Mismo patrón de página que {@code people.application.EmployeeAdminQueryService.EmployeePage}. */
    public record ContractPage(List<EmploymentContract> items, Long nextAfterId) {
    }

    @Transactional
    public ContractPage search(Long employeeId, Long companyId, ContractStatus status, Long afterId, int limit) {
        Pageable page = PageRequest.of(0, limit + 1);
        List<EmploymentContract> rows = contractRepository.search(employeeId, companyId, status, afterId, page);

        boolean hasMore = rows.size() > limit;
        List<EmploymentContract> pageRows = hasMore ? rows.subList(0, limit) : rows;
        Long nextAfterId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new ContractPage(pageRows, nextAfterId);
    }

    @Transactional
    public EmploymentContract getById(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new NoSuchElementException("Contrato " + contractId + " no encontrado"));
    }

    /** Registro de salario abierto (sin {@code effectiveTo}), o vacío si el contrato quedó sin uno — no debería pasar, ver {@code ContractPayrollQueryService}. */
    @Transactional
    public Optional<SalaryHistory> openSalary(Long contractId) {
        return salaryHistoryRepository.findByContractIdAndEffectiveToIsNull(contractId);
    }

    @Transactional
    public List<SalaryHistory> salaryHistory(Long contractId) {
        return salaryHistoryRepository.findByContractId(contractId);
    }

    @Transactional
    public List<ContractAmendment> amendments(Long contractId) {
        return amendmentRepository.findByContractId(contractId);
    }

    @Transactional
    public Optional<Termination> termination(Long contractId) {
        return terminationRepository.findByContractId(contractId);
    }
}
