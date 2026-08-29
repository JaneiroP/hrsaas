package com.ohmdevs.hrsaas.contracts.api;

import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.contracts.infrastructure.EmploymentContractRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.SalaryHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Único contenido público de {@code contracts}: contratos activos de una empresa y su salario vigente. */
@Service
public class ContractPayrollQueryService {

    private final EmploymentContractRepository contractRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;

    public ContractPayrollQueryService(EmploymentContractRepository contractRepository,
                                        SalaryHistoryRepository salaryHistoryRepository) {
        this.contractRepository = contractRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
    }

    /**
     * Un contrato sin registro salarial abierto (nunca debería pasar, pero
     * la firma de {@code SalaryHistoryRepository} devuelve
     * {@code Optional}) se excluye del resultado en vez de romper toda la
     * corrida — un dato inconsistente para un empleado no debe bloquear el
     * cálculo de los demás.
     */
    @Transactional
    public List<ActiveContractSummary> activeContractsForCompany(Long companyId) {
        List<EmploymentContract> contracts = contractRepository.findByCompanyIdAndStatus(
                companyId, ContractStatus.ACTIVE);
        return contracts.stream()
                .map(this::toSummary)
                .filter(Objects::nonNull)
                .toList();
    }

    private ActiveContractSummary toSummary(EmploymentContract contract) {
        SalaryHistory openSalary = salaryHistoryRepository
                .findByContractIdAndEffectiveToIsNull(contract.getId())
                .orElse(null);
        if (openSalary == null) {
            return null;
        }
        return new ActiveContractSummary(
                contract.getId(), contract.getEmployeeId(), contract.getCompanyId(), contract.getCostCenterId(),
                openSalary.getAmount(), openSalary.getCurrency());
    }
}
