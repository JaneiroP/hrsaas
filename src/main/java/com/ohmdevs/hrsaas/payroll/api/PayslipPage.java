package com.ohmdevs.hrsaas.payroll.api;

import java.util.List;

/**
 * Una página de {@link PayslipSummaryView}. {@code nextAfterId} es el id
 * crudo de la última fila devuelta, o {@code null} si no hay más — codificarlo
 * como el cursor opaco que ve el cliente es trabajo de {@code portal}
 * ({@code shared.web.CursorCodec}), no de este módulo.
 */
public record PayslipPage(List<PayslipSummaryView> items, Long nextAfterId) {
}
