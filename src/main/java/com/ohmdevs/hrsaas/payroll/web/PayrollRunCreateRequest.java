package com.ohmdevs.hrsaas.payroll.web;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/payroll/companies/{companyId}/runs}. */
public record PayrollRunCreateRequest(@NotNull Long payrollPeriodId) {
}
