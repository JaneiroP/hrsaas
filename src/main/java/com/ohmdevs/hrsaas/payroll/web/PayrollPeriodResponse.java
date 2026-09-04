package com.ohmdevs.hrsaas.payroll.web;

import java.time.LocalDate;

/** Proyección administrativa de {@code PayrollPeriod}. */
public record PayrollPeriodResponse(
        Long id, Long companyId, String name, String frequency,
        LocalDate startDate, LocalDate endDate, LocalDate payDate) {
}
