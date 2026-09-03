package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

/** Sin {@code code}: es la clave de negocio, ver Javadoc de {@code OrgStructureService}. */
public record CostCenterUpdateRequest(@NotBlank String name, boolean active) {
}
