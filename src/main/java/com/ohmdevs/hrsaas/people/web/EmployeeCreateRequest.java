package com.ohmdevs.hrsaas.people.web;

import com.ohmdevs.hrsaas.people.domain.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/employees}. Da de alta solo el expediente
 * ({@code Employee}, estado {@code PENDING}) — contratarlo con una empresa y
 * un cargo es un paso aparte, {@code POST /api/v1/contracts}, porque es
 * exactamente lo que decide que el expediente pase a {@code ACTIVE} (ver
 * Javadoc de {@code EmployeeStatus}).
 */
public record EmployeeCreateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Past LocalDate birthDate,
        @NotNull DocumentType documentType,
        @NotBlank String documentNumber,
        @Email String email,
        String phone) {
}
