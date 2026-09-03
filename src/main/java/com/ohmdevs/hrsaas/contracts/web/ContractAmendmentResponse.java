package com.ohmdevs.hrsaas.contracts.web;

import java.time.LocalDate;

/** {@code detail} viaja como el JSON crudo guardado en {@code contract_amendments.detail} — su forma varía según {@code amendmentType}. */
public record ContractAmendmentResponse(
        Long id, String amendmentType, LocalDate effectiveDate, String detail, String notes) {
}
