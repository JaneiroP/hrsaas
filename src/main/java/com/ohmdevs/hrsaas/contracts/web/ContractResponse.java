package com.ohmdevs.hrsaas.contracts.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Proyección administrativa de {@code EmploymentContract}, enriquecida con
 * nombre de empleado/empresa/cargo resueltos vía {@code people.api}/{@code
 * org.api} — nunca con {@code @ManyToOne} desde la entidad (ver Javadoc de
 * {@code contracts.package-info}).
 *
 * <p>{@code employeeMaskedDocumentNumber} ya viene enmascarado (AD-08); igual
 * que en {@code people.web.EmployeeResponse}, no existe hoy un camino para
 * destaparlo desde este endpoint.
 */
public record ContractResponse(
        Long id, Long employeeId, String employeeName, String employeeMaskedDocumentNumber,
        Long companyId, String companyName, Long positionId, String positionTitle,
        Long costCenterId, Long supervisorEmployeeId, String contractType,
        LocalDate startDate, LocalDate endDate, LocalDate probationEndsAt, String status,
        BigDecimal currentSalary, String currency, Instant createdAt) {
}
