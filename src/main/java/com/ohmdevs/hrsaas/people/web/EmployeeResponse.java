package com.ohmdevs.hrsaas.people.web;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Proyección administrativa de {@link com.ohmdevs.hrsaas.people.domain.Employee}.
 * {@code maskedDocumentNumber} nunca es el valor en claro (AD-08) — no existe
 * hoy ningún permiso ni endpoint que lo destape para este módulo, a
 * diferencia de {@code employee:read_bank} para cuentas bancarias.
 */
public record EmployeeResponse(
        Long id, String firstName, String lastName, String fullName, String email, String phone,
        LocalDate birthDate, String documentType, String maskedDocumentNumber, String status, Instant createdAt) {
}
