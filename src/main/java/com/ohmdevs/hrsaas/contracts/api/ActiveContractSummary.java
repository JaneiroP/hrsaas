package com.ohmdevs.hrsaas.contracts.api;

import java.math.BigDecimal;

/** Lo que {@code payroll} necesita de un contrato activo: quién es, y su salario vigente. */
public record ActiveContractSummary(
        Long contractId,
        Long employeeId,
        Long companyId,
        Long positionId,
        BigDecimal currentSalary,
        String currency) {
}
