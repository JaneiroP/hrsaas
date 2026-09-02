package com.ohmdevs.hrsaas.time.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una solicitud de licencia para el portal. {@code status} viaja como el
 * nombre crudo de {@code LeaveRequestStatus} ({@code "PENDING"},
 * {@code "APPROVED"}...) — traducir a la etiqueta en español que espera el
 * frontend ({@code PENDIENTE}, {@code APROBADA}...) es trabajo de
 * {@code portal}, no de este módulo.
 */
public record LeaveRequestView(
        Long requestId, String leaveTypeCode, String leaveTypeLabel, LocalDate startDate, LocalDate endDate,
        BigDecimal days, String status, String reason, Instant requestedAt) {
}
