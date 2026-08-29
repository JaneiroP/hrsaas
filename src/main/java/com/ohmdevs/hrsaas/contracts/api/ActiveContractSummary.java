package com.ohmdevs.hrsaas.contracts.api;

import java.math.BigDecimal;

/**
 * Lo que {@code payroll} necesita de un contrato activo: quién es, su
 * salario vigente, y su centro de costo (para costo laboral / resúmenes por
 * centro de costo — blueprint §12 Fase 4; {@code costCenterId} nulo si el
 * contrato no tiene uno asignado).
 */
public record ActiveContractSummary(
        Long contractId,
        Long employeeId,
        Long companyId,
        Long costCenterId,
        BigDecimal currentSalary,
        String currency) {
}
