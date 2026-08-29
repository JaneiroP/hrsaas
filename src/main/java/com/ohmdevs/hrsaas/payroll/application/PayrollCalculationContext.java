package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;

import java.math.BigDecimal;

/**
 * Estado en curso del cálculo de un volante, pasado a cada
 * {@code ConceptCalculationStrategy} en el orden de la tubería. No se
 * persiste — vive solo durante {@code PayrollCalculationService.calculateForContract}.
 *
 * <p>Las bases nombradas ({@link #base(String)}) son la forma en que un
 * concepto {@code RATE_OF_BASE}/{@code BRACKET_TABLE} referencia "lo ya
 * calculado en esta misma corrida" sin que la tubería exponga una API
 * genérica de "leer cualquier concepto anterior por código" — el orden de
 * la tubería (blueprint §04) sigue fijo en {@code PayrollCalculationService},
 * esto solo expone los acumulados que ese orden ya garantiza consistentes en
 * el momento en que cada paso los lee.
 */
public class PayrollCalculationContext {

    private final ActiveContractSummary contract;
    private final AttendanceSummary attendance;

    private BigDecimal totalEarnings = BigDecimal.ZERO;
    private BigDecimal taxableEarnings = BigDecimal.ZERO;
    private BigDecimal tssEmployeeDeductions = BigDecimal.ZERO;

    public PayrollCalculationContext(ActiveContractSummary contract, AttendanceSummary attendance) {
        this.contract = contract;
        this.attendance = attendance;
    }

    public ActiveContractSummary contract() {
        return contract;
    }

    public AttendanceSummary attendance() {
        return attendance;
    }

    /** Registra un devengo ya calculado — antes de que el siguiente paso de la tubería pueda leerlo vía {@link #base(String)}. */
    public void recordEarning(BigDecimal amount, boolean taxableForIsr) {
        totalEarnings = totalEarnings.add(amount);
        if (taxableForIsr) {
            taxableEarnings = taxableEarnings.add(amount);
        }
    }

    /** Registra una deducción de TSS a cargo del empleado ya calculada — la base ISR (paso 5) la resta de lo gravable. */
    public void recordTssEmployeeDeduction(BigDecimal amount) {
        tssEmployeeDeductions = tssEmployeeDeductions.add(amount);
    }

    /**
     * @param key {@code GROSS} (todos los devengos hasta ahora) o
     *            {@code ISR_TAXABLE_BASE} (devengos gravables menos TSS
     *            deducible del empleado, sin bajar de cero).
     */
    public BigDecimal base(String key) {
        return switch (key) {
            case "GROSS" -> totalEarnings;
            case "ISR_TAXABLE_BASE" -> taxableEarnings.subtract(tssEmployeeDeductions).max(BigDecimal.ZERO);
            default -> throw new IllegalArgumentException("Base desconocida en formulaParams: " + key);
        };
    }
}
