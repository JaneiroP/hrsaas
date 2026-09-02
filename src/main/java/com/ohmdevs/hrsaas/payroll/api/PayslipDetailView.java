package com.ohmdevs.hrsaas.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Desglose completo de un volante (blueprint §09: {@code GET /me/payslips/{id}}). */
public record PayslipDetailView(
        Long payslipId, String periodName, LocalDate payDate, String runStatus,
        BigDecimal grossPay, BigDecimal netPay, String currency, Long companyId,
        List<PayslipLineView> earnings, List<PayslipLineView> deductions, BigDecimal totalDeductions) {
}
