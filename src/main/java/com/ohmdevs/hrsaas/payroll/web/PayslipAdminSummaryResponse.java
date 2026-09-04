package com.ohmdevs.hrsaas.payroll.web;

import java.math.BigDecimal;

/**
 * Un volante en el listado de una corrida (alcance {@code COMPANY}, para
 * cualquier empleado de la corrida) — análoga a
 * {@code payroll.api.PayslipSummaryView}, pero con {@code employeeId} en vez
 * de omitirlo: la vista de autoservicio no lo necesita (es implícitamente
 * "de quien pregunta"), esta sí, porque el actor administrativo consulta el
 * volante de otro.
 */
public record PayslipAdminSummaryResponse(
        Long payslipId, Long employeeId, Long contractId, Long costCenterId,
        BigDecimal grossPay, BigDecimal netPay, String currency) {
}
