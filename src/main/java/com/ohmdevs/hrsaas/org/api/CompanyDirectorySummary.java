package com.ohmdevs.hrsaas.org.api;

/** Lo que {@code portal} necesita de una razón social para el perfil del empleado. */
public record CompanyDirectorySummary(Long companyId, String legalName, String tradeName, String rnc) {
}
