package com.ohmdevs.hrsaas.platform.web;

/** Forma de respuesta acordada con el frontend ({@code AuthService.login}): sin refresh token todavía. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn);
    }
}
