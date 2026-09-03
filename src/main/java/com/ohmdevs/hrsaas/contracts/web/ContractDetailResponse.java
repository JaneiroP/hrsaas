package com.ohmdevs.hrsaas.contracts.web;

import java.util.List;

/** {@code termination} es {@code null} salvo que el contrato esté {@code TERMINATED}. */
public record ContractDetailResponse(
        ContractResponse contract,
        List<SalaryHistoryResponse> salaryHistory,
        List<ContractAmendmentResponse> amendments,
        TerminationResponse termination) {
}
