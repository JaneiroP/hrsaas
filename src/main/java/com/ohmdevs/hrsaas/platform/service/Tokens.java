package com.ohmdevs.hrsaas.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generación y digest de tokens de un solo uso. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private Tokens() {
    }

    /** 256 bits de entropía en Base64 URL-safe: seguro para pegar en un enlace de correo. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 en hexadecimal (64 caracteres, que es justo el ancho de la columna).
     *
     * <p>SHA-256 a secas, sin BCrypt, es lo correcto <em>aquí</em>: el token ya
     * tiene 256 bits aleatorios, así que no hay nada que un ataque de diccionario
     * pueda adivinar y el coste de un hash lento no compraría nada. Para
     * contraseñas elegidas por personas la respuesta es la contraria.
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
