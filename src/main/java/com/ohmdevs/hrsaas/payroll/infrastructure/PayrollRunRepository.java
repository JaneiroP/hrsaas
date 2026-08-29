package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    List<PayrollRun> findByPayrollPeriodId(Long payrollPeriodId);

    List<PayrollRun> findByCompanyId(Long companyId);
}
