package com.ohmdevs.hrsaas.contracts.web;

import com.ohmdevs.hrsaas.contracts.domain.TerminationCause;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/contracts/{id}/terminate}. {@code cause}
 * (provisional, ver Javadoc de {@link TerminationCause}) es lo que decidirá
 * si se paga cesantía/preaviso cuando exista el motor de nómina (Fase 3 ya
 * construida, pero ese cálculo todavía no lee esta causal).
 */
public record TerminationRequest(
        @NotNull LocalDate terminationDate,
        @NotNull TerminationCause cause,
        boolean noticeGiven,
        boolean severanceEligible,
        String notes) {
}
