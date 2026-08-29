package com.ohmdevs.hrsaas.payroll.domain;

/** Frecuencia de un {@link PayrollPeriod}. Blueprint §14, decisión abierta #4: se asume mensual y quincenal. */
public enum PayrollFrequency {
    MONTHLY,
    BIWEEKLY
}
