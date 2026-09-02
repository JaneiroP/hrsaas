package com.ohmdevs.hrsaas.portal.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Forma exacta de {@code PayslipDetail} ({@code core/models/payslip.model.ts}). */
public record PayslipDetailResponse(
        String id, String period, LocalDate payDateIso, String status, BigDecimal grossAmount, BigDecimal netAmount,
        List<PayslipLineResponse> earnings, List<PayslipLineResponse> deductions, BigDecimal totalDeductions,
        String companyName, String maskedNationalId) {
}
