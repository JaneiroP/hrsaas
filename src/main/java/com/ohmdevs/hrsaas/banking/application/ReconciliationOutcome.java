package com.ohmdevs.hrsaas.banking.application;

/**
 * El resultado, ya interpretado, del archivo de retorno de un banco para un
 * ítem — el parseo del archivo real (formato propio de cada banco) queda
 * fuera de esta pasada (ver {@code banking/package-info.java}); este record
 * es el punto de entrada al que ese parseo futuro alimentaría sus
 * resultados, y por el que un test o un caso manual puede alimentar los
 * suyos hoy.
 */
public record ReconciliationOutcome(Long paymentBatchItemId, boolean accepted, String rejectionCode, String rejectionReason) {
}
