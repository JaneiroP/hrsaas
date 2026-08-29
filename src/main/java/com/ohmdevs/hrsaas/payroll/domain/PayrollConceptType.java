package com.ohmdevs.hrsaas.payroll.domain;

/**
 * Naturaleza de un {@link PayrollConcept}. Solo {@code EARNING}/
 * {@code DEDUCTION} afectan el neto a pagar del empleado — los otros dos van
 * a {@link Accrual}, en paralelo, sin tocar el neto (blueprint §04).
 */
public enum PayrollConceptType {
    EARNING,
    DEDUCTION,
    EMPLOYER_CONTRIBUTION,
    PROVISION
}
