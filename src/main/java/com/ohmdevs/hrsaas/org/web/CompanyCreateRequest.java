package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code POST /api/v1/org/companies}. Sin validación de dígito
 * verificador de RNC (mismo límite documentado en {@link
 * com.ohmdevs.hrsaas.org.domain.Company}): solo se exige la forma de 9 u 11
 * dígitos, pendiente de confirmar el algoritmo real con un contador
 * dominicano.
 */
public record CompanyCreateRequest(
        @NotBlank String legalName,
        String tradeName,
        @NotBlank @Pattern(regexp = "\\d{9}|\\d{11}", message = "El RNC debe tener 9 u 11 dígitos") String rnc) {
}
