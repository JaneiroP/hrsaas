package com.ohmdevs.hrsaas.contracts.infrastructure;

import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    List<SalaryHistory> findByContractId(Long contractId);

    /** El índice único parcial de la migración garantiza a lo sumo un registro abierto por contrato. */
    Optional<SalaryHistory> findByContractIdAndEffectiveToIsNull(Long contractId);
}
