package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code payGradeId} es opcional: no toda empresa piloto define bandas salariales desde el día uno. */
public record PositionCreateRequest(
        @NotNull Long departmentId,
        @NotBlank String title,
        Long payGradeId) {
}
