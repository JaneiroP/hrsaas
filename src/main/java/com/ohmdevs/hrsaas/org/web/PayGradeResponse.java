package com.ohmdevs.hrsaas.org.web;

import java.math.BigDecimal;

public record PayGradeResponse(
        Long id, Long companyId, String code, String name,
        BigDecimal minSalary, BigDecimal maxSalary, String currency, boolean active) {
}
