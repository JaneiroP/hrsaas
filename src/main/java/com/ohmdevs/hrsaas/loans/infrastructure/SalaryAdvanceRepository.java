package com.ohmdevs.hrsaas.loans.infrastructure;

import com.ohmdevs.hrsaas.loans.domain.SalaryAdvance;
import com.ohmdevs.hrsaas.loans.domain.SalaryAdvanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, Long> {

    List<SalaryAdvance> findByEmployeeId(Long employeeId);

    List<SalaryAdvance> findByEmployeeIdAndStatusAndDeductionDateBetween(
            Long employeeId, SalaryAdvanceStatus status, LocalDate from, LocalDate to);
}
