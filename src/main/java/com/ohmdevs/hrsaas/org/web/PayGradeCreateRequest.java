package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayGradeCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull BigDecimal minSalary,
        @NotNull BigDecimal maxSalary) {
}
