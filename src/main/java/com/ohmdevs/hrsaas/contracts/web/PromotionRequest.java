package com.ohmdevs.hrsaas.contracts.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Cuerpo de {@code POST /api/v1/contracts/{id}/promote}: cambia de cargo sin abrir un contrato nuevo (queda en {@code employment_contracts_aud} vía Envers). */
public record PromotionRequest(
        @NotNull Long newPositionId,
        @NotNull LocalDate effectiveDate,
        String notes) {
}
