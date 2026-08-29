package com.ohmdevs.hrsaas.loans.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que {@code payroll} (Fase 3) necesita de {@code loans} para un empleado
 * y un período: cuotas de préstamo pendientes en el rango, avances
 * aprobados con descuento programado en el rango, y embargos activos en
 * orden de prelación.
 *
 * <p>Los montos de {@code garnishments} son los declarados en la orden
 * judicial ({@code fixedAmount} o {@code percentage}), sin prorratear —
 * aplicar el mínimo inembargable cuando varios compiten por el mismo neto es
 * cálculo de {@code payroll} (AD-04), no de este resumen.
 */
public record PayrollDeductionSummary(
        List<LoanInstallmentDue> loanInstallments,
        List<SalaryAdvanceDue> salaryAdvances,
        List<GarnishmentDue> garnishments) {

    public record LoanInstallmentDue(Long loanAgreementId, Long installmentId, BigDecimal amount) {
    }

    public record SalaryAdvanceDue(Long salaryAdvanceId, BigDecimal amount) {
    }

    public record GarnishmentDue(Long garnishmentOrderId, int priority, BigDecimal fixedAmount, BigDecimal percentage) {
    }
}
