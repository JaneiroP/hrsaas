package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollConceptRepository extends JpaRepository<PayrollConcept, Long> {

    List<PayrollConcept> findByActiveTrueOrderByExecutionOrderAsc();
}
