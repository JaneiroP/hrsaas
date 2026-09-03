package com.ohmdevs.hrsaas.contracts.web;

import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/contracts} — "contratar": abre el contrato y
 * el primer registro de {@code SalaryHistory} ({@code ContractLifecycleService.hire}),
 * y marca el expediente como {@code ACTIVE} en {@code people}.
 * {@code costCenterId}/{@code supervisorEmployeeId}/{@code endDate}/
 * {@code probationEndsAt} son opcionales.
 */
public record ContractCreateRequest(
        @NotNull Long employeeId,
        @NotNull Long companyId,
        @NotNull Long positionId,
        Long costCenterId,
        Long supervisorEmployeeId,
        @NotNull ContractType contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        LocalDate probationEndsAt,
        @NotNull @Positive BigDecimal initialSalary,
        @NotBlank String currency) {
}
