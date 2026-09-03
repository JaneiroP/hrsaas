package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Sin {@code code}: es la clave de negocio, ver Javadoc de {@code OrgStructureService}. */
public record PayGradeUpdateRequest(
        @NotBlank String name, @NotNull BigDecimal minSalary, @NotNull BigDecimal maxSalary, boolean active) {
}
