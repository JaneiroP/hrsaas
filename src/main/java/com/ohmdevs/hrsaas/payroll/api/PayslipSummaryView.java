package com.ohmdevs.hrsaas.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un volante en la lista histórica del portal (blueprint §09:
 * {@code GET /me/payslips}). {@code runStatus} es el nombre crudo de
 * {@code PayrollRunStatus} ({@code "PAGADA"}, {@code "CALCULADA"}...), no el
 * enum: quien lo traduzca a una etiqueta de UI (p. ej. "PAGADO"/"PROCESANDO")
 * es {@code portal}, no este módulo — {@code payroll.api} no conoce ese
 * vocabulario de presentación, mismo motivo por el que
 * {@code PrimaryBankAccountSummary} devuelve {@code accountType} como cadena.
 */
public record PayslipSummaryView(
        Long payslipId, String periodName, LocalDate payDate, String runStatus,
        BigDecimal grossPay, BigDecimal netPay, String currency) {
}
