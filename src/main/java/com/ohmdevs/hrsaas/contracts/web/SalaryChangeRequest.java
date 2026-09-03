package com.ohmdevs.hrsaas.contracts.web;

import com.ohmdevs.hrsaas.contracts.domain.SalaryChangeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cuerpo de {@code POST /api/v1/contracts/{id}/salary-changes}: cierra el registro salarial abierto y abre uno nuevo, nunca edita el histórico. */
public record SalaryChangeRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotNull LocalDate effectiveFrom,
        @NotNull SalaryChangeReason reason) {
}
