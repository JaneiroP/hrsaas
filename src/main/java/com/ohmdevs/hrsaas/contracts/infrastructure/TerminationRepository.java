package com.ohmdevs.hrsaas.contracts.infrastructure;

import com.ohmdevs.hrsaas.contracts.domain.Termination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerminationRepository extends JpaRepository<Termination, Long> {

    Optional<Termination> findByContractId(Long contractId);
}
