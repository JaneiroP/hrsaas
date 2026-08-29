package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipLineRepository extends JpaRepository<PayslipLine, Long> {

    List<PayslipLine> findByPayslipIdOrderByLineOrderAsc(Long payslipId);
}
