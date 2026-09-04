package com.ohmdevs.hrsaas.payroll.web;

import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Cuerpo de {@code POST /api/v1/payroll/companies/{companyId}/periods}. */
public record PayrollPeriodCreateRequest(
        @NotBlank String name,
        @NotNull PayrollFrequency frequency,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalDate payDate) {
}
