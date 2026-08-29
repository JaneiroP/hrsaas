package com.ohmdevs.hrsaas.time.api;

import java.math.BigDecimal;

/**
 * Lo que {@code payroll} (Fase 3) necesita de {@code time} para un empleado y
 * un período: días efectivos, horas extra, y días de ausencia sin
 * remuneración — la definición exacta de "Terminado cuando" de la Fase 2 del
 * blueprint.
 */
public record AttendanceSummary(BigDecimal effectiveDays, BigDecimal overtimeHours, BigDecimal unpaidLeaveDays) {
}
