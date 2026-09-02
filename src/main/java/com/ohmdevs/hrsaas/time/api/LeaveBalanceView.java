package com.ohmdevs.hrsaas.time.api;

import java.math.BigDecimal;

/**
 * Saldo de un tipo de licencia para el portal (blueprint §09:
 * {@code GET /me/leave-balances}). {@code totalDays}/{@code availableDays}
 * nulos significan "sin tope fijo" (p. ej. licencia médica) — el mismo tipo
 * de licencia puede o no devengar saldo, decisión de
 * {@code LeaveType.accrualDaysPerYear} en {@code time.domain}.
 */
public record LeaveBalanceView(
        String leaveTypeCode, String label, BigDecimal totalDays, BigDecimal takenDays,
        BigDecimal availableDays, String note) {
}
