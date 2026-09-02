package com.ohmdevs.hrsaas.portal.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Forma exacta de {@code NewLeaveRequest} ({@code core/models/leave.model.ts}). */
public record NewLeaveRequestCommand(
        @NotBlank String type,
        @NotNull LocalDate startDateIso,
        @NotNull LocalDate endDateIso,
        String reason) {
}
