package com.ohmdevs.hrsaas.contracts.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaryHistoryResponse(
        Long id, BigDecimal amount, String currency, LocalDate effectiveFrom, LocalDate effectiveTo, String reason) {
}
