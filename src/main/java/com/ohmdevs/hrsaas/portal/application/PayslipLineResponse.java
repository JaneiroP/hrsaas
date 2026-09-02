package com.ohmdevs.hrsaas.portal.application;

import java.math.BigDecimal;

/** Forma de {@code PayslipDetail.earnings[]}/{@code .deductions[]} ({@code core/models/payslip.model.ts}). */
public record PayslipLineResponse(String label, BigDecimal amount) {
}
