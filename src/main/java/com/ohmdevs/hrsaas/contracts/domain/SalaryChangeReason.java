package com.ohmdevs.hrsaas.contracts.domain;

/** Motivo de un registro en {@link SalaryHistory}. */
public enum SalaryChangeReason {
    HIRE,
    RAISE,
    PROMOTION,
    DEMOTION,
    CORRECTION,
    ADJUSTMENT
}
