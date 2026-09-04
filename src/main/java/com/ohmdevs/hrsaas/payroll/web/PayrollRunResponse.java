package com.ohmdevs.hrsaas.payroll.web;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Proyección administrativa de {@code PayrollRun}, con el período aplanado
 * dentro (nombre, frecuencia y fechas) para que el frontend no tenga que
 * encadenar una segunda llamada a {@code GET .../periods} solo para mostrar
 * "Nómina de Enero 2025" junto al estado de la corrida.
 */
public record PayrollRunResponse(
        Long id, Long companyId,
        Long payrollPeriodId, String periodName, String frequency,
        LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate,
        String status, String configVersionId, Long reversalOfRunId,
        Instant createdAt, Instant calculatedAt, Instant approvedAt, Long approvedByUserId,
        Instant paidAt, Instant closedAt) {
}
