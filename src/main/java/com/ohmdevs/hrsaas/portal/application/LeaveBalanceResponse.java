package com.ohmdevs.hrsaas.portal.application;

import java.math.BigDecimal;

/** Forma exacta de {@code LeaveBalance} ({@code core/models/leave.model.ts}). */
public record LeaveBalanceResponse(
        String type, String label, BigDecimal totalDays, BigDecimal takenDays, BigDecimal availableDays, String note) {
}
