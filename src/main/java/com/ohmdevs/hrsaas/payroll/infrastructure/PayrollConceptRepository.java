package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollConceptRepository extends JpaRepository<PayrollConcept, Long> {

    Optional<PayrollConcept> findByCode(String code);

    List<PayrollConcept> findByActiveTrueOrderByExecutionOrderAsc();
}
