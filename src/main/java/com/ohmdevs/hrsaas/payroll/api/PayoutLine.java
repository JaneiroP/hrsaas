package com.ohmdevs.hrsaas.payroll.api;

import java.math.BigDecimal;

/** Lo que {@code banking} necesita de un volante para pagarlo: quién es y cuánto se le debe depositar. */
public record PayoutLine(Long employeeId, Long contractId, BigDecimal netPay, String currency) {
}
