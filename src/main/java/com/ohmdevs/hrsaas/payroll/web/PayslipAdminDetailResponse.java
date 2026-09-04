package com.ohmdevs.hrsaas.payroll.web;

import com.ohmdevs.hrsaas.payroll.api.PayslipLineView;

import java.math.BigDecimal;
import java.util.List;

/**
 * Desglose completo de un volante para la API administrativa — mismo shape
 * que {@code payroll.api.PayslipDetailView} en las líneas (se reutiliza
 * {@link PayslipLineView} tal cual), pero identificado por {@code payslipId}
 * dentro de una corrida conocida en vez de por el histórico de un empleado.
 */
public record PayslipAdminDetailResponse(
        Long payslipId, Long employeeId, Long contractId, Long costCenterId,
        BigDecimal grossPay, BigDecimal netPay, String currency,
        List<PayslipLineView> earnings, List<PayslipLineView> deductions, BigDecimal totalDeductions) {
}
