package com.ohmdevs.hrsaas.contracts.api;

import java.time.LocalDate;

/**
 * Lo que {@code portal} necesita del contrato vigente de un empleado para su
 * perfil (blueprint §09: {@code GET /me} — "perfil, contrato vigente,
 * supervisor"). {@code contractType} viaja como {@code String}, no el enum de
 * {@code contracts.domain}, mismo patrón que {@link ActiveContractSummary}
 * con {@code currency}.
 */
public record EmployeeEmploymentSummary(
        Long contractId,
        Long employeeId,
        Long companyId,
        Long positionId,
        Long supervisorEmployeeId,
        String contractType,
        LocalDate startDate) {
}
