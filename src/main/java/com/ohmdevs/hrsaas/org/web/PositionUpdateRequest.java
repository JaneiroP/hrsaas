package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

/** Sin {@code departmentId}: mover un cargo de departamento es un cambio estructural distinto de editarlo (ver Javadoc de {@code OrgStructureService.updatePosition}). */
public record PositionUpdateRequest(@NotBlank String title, Long payGradeId, boolean active) {
}
