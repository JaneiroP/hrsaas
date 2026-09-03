package com.ohmdevs.hrsaas.org.web;

/** Proyección administrativa de {@link com.ohmdevs.hrsaas.org.domain.Company}, sin exponer la entidad JPA por HTTP. */
public record CompanyResponse(Long id, String legalName, String tradeName, String rnc, boolean active) {
}
