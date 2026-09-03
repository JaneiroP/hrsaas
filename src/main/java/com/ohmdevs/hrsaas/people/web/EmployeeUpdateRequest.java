package com.ohmdevs.hrsaas.people.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code PATCH /api/v1/employees/{id}}. Sin {@code documentType}/
 * {@code documentNumber}/{@code birthDate}: ver Javadoc de
 * {@code EmployeeRecordService.updateEmployee}.
 */
public record EmployeeUpdateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email String email,
        String phone) {
}
