package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code PATCH /api/v1/org/companies/{id}}. Sin {@code rnc}: ver Javadoc de {@code OrgStructureService}. */
public record CompanyUpdateRequest(@NotBlank String legalName, String tradeName, boolean active) {
}
