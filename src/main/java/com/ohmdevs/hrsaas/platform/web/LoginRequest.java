package com.ohmdevs.hrsaas.platform.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/v1/auth/login}, forma acordada con el frontend. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
