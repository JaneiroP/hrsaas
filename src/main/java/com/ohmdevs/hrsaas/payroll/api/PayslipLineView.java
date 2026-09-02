package com.ohmdevs.hrsaas.payroll.api;

import java.math.BigDecimal;

/**
 * Una línea del desglose de un volante, ya con signo positivo tanto para
 * devengos como para deducciones — a diferencia de {@code PayslipLine.amount}
 * (con signo, negativo en deducciones, para que sumar dé el neto), aquí el
 * signo ya no aporta nada: el consumidor ({@code portal}) separa devengos de
 * deducciones en dos listas distintas, así que mostrar "-3,000.00" en la
 * lista de deducciones sería redundante con su propio encabezado.
 */
public record PayslipLineView(String label, BigDecimal amount) {
}
