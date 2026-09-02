package com.ohmdevs.hrsaas.portal.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Forma exacta de {@code PayslipSummary} ({@code core/models/payslip.model.ts}). */
public record PayslipSummaryResponse(
        String id, String period, LocalDate payDateIso, String status, BigDecimal grossAmount, BigDecimal netAmount) {
}
