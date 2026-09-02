package com.ohmdevs.hrsaas.contracts.api;

import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.infrastructure.EmploymentContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Segundo contenido público de {@code contracts}, aparte de
 * {@link ContractPayrollQueryService}: el contrato vigente de <em>un</em>
 * empleado, para {@code portal} (perfil de autoservicio) — a diferencia de
 * aquel, que lista los de <em>toda una empresa</em> para {@code payroll}.
 */
@Service
public class EmployeeEmploymentQueryService {

    private final EmploymentContractRepository contractRepository;

    public EmployeeEmploymentQueryService(EmploymentContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /** "Vigente" es no terminado (ACTIVE o SUSPENDED), igual que {@code ContractLifecycleService.hire} lo exige para no duplicar contrato. */
    @Transactional
    public Optional<EmployeeEmploymentSummary> activeEmploymentFor(Long employeeId) {
        return contractRepository.findByEmployeeIdAndStatusNot(employeeId, ContractStatus.TERMINATED)
                .map(EmployeeEmploymentQueryService::toSummary);
    }

    private static EmployeeEmploymentSummary toSummary(EmploymentContract contract) {
        return new EmployeeEmploymentSummary(
                contract.getId(), contract.getEmployeeId(), contract.getCompanyId(), contract.getPositionId(),
                contract.getSupervisorEmployeeId(), contract.getContractType().name(), contract.getStartDate());
    }
}
