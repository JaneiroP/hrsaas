package com.ohmdevs.hrsaas.contracts.infrastructure;

import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    List<EmploymentContract> findByEmployeeId(Long employeeId);

    /** El índice único parcial de la migración garantiza a lo sumo un resultado. */
    Optional<EmploymentContract> findByEmployeeIdAndStatusNot(Long employeeId, ContractStatus status);
}
