package com.ohmdevs.hrsaas.payroll.domain;

/**
 * Cómo se calcula un {@link PayrollConcept} (blueprint §04: "conceptos como
 * datos, no como {@code if}"). {@code SCRIPT} — el último recurso que
 * describe el blueprint, código de cliente en sandbox con timeout — se deja
 * fuera a propósito de esta fase: es la estrategia de mayor riesgo
 * (superficie de ejecución de código arbitrario) y ninguna corrida de
 * prueba la necesita todavía.
 */
public enum FormulaStrategy {
    /** Monto fijo, tomado de {@code PayrollConcept.formulaParams}. */
    FIXED,
    /** Tasa sobre una base (el salario, u otro concepto ya calculado en la misma corrida), con tope opcional. */
    RATE_OF_BASE,
    /** Horas (típicamente extra) por una tarifa con multiplicador. */
    HOURS_TIMES_RATE,
    /** Tabla de tramos progresivos de {@code taxconfig} (el ISR, por ejemplo). */
    BRACKET_TABLE
}
