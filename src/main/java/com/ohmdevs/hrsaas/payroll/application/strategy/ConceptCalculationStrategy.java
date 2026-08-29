package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una forma de calcular el monto de un {@link PayrollConcept} (blueprint
 * §04: "conceptos como datos, no como {@code if}") — la tubería en
 * {@code PayrollCalculationService} despacha por {@link #handles()}, nunca
 * con un {@code switch} sobre {@link FormulaStrategy} desperdigado por el
 * código.
 *
 * <p>{@link #calculate} devuelve siempre una magnitud <strong>sin
 * signo</strong> a escala intermedia ({@code Money.INTERMEDIATE_SCALE}) — el
 * signo (positivo/negativo según {@code EARNING}/{@code DEDUCTION}) y el
 * redondeo final a {@code Money.SCALE} los aplica quien llama, no la
 * estrategia.
 */
public interface ConceptCalculationStrategy {

    FormulaStrategy handles();

    BigDecimal calculate(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate);
}
