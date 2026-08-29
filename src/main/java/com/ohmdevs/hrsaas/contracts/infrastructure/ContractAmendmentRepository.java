package com.ohmdevs.hrsaas.contracts.infrastructure;

import com.ohmdevs.hrsaas.contracts.domain.ContractAmendment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractAmendmentRepository extends JpaRepository<ContractAmendment, Long> {

    List<ContractAmendment> findByContractId(Long contractId);
}
