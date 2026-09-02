package com.ohmdevs.hrsaas.portal.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Forma exacta de {@code LeaveRequest} ({@code core/models/leave.model.ts}). */
public record LeaveRequestResponse(
        String id, String type, String typeLabel, LocalDate startDateIso, LocalDate endDateIso,
        BigDecimal days, String status, String reason, LocalDate requestedAtIso) {
}
