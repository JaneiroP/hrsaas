package com.ohmdevs.hrsaas.contracts.web;

import java.time.LocalDate;

public record TerminationResponse(
        Long id, LocalDate terminationDate, String cause, boolean noticeGiven, boolean severanceEligible, String notes) {
}
