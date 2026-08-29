package com.ohmdevs.hrsaas.contracts.domain;

/**
 * Causal de desvinculación. <strong>Provisional, sin validar legalmente</strong> —
 * confirmar con abogado laboral/contador antes de que {@code taxconfig}/{@code payroll}
 * decidan qué causal dispara cesantía o preaviso (Código de Trabajo dominicano).
 */
public enum TerminationCause {
    RESIGNATION,
    JUSTIFIED_DISMISSAL,
    UNJUSTIFIED_DISMISSAL,
    MUTUAL_AGREEMENT,
    CONTRACT_EXPIRATION,
    DEATH
}
