package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccrualRepository extends JpaRepository<Accrual, Long> {

    List<Accrual> findByPayrollRunId(Long payrollRunId);

    List<Accrual> findByEmployeeId(Long employeeId);
}
