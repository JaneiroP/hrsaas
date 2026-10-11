package com.ohmdevs.hrsaas.integrations.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba la cabecera {@code X-Attendance-Signature} de un webhook de
 * asistencia: {@code t=<segundos unix>,v1=<hex de HMAC-SHA256>}, donde lo
 * firmado es el texto {@code <t>.<cuerpo>} con el secreto del conector.
 *
 * <p>Se verifica sobre los bytes del cuerpo tal como llegaron, antes de
 * interpretarlos como JSON: cualquier cosa que reordene o reformatee el
 * cuerpo cambia la firma.
 *
 * <p>La marca de tiempo forma parte de lo firmado y se exige reciente. Sin
 * eso, quien capturara una petición válida podría reenviarla meses después
 * (blueprint §08: "timestamp anti-replay").
 */
public final class WebhookSignatureVerifier {

    /** Margen para relojes desajustados entre los dos sistemas y para el tiempo en tránsito. */
    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern HEADER = Pattern.compile("t=(\\d{1,12}),v1=([0-9a-f]{64})");

    private WebhookSignatureVerifier() {
    }

    public static boolean isValid(String secret, String header, byte[] body, Instant now) {
        if (secret == null || header == null) {
            return false;
        }
        Matcher parts = HEADER.matcher(header);
        if (!parts.matches()) {
            return false;
        }
        String timestamp = parts.group(1);
        Duration age = Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), now).abs();
        if (age.compareTo(TOLERANCE) > 0) {
            return false;
        }
        // Comparación en tiempo constante: la duración de la respuesta no debe
        // decir cuántos caracteres de la firma eran correctos.
        return MessageDigest.isEqual(
                sign(secret, timestamp, body).getBytes(StandardCharsets.US_ASCII),
                parts.group(2).getBytes(StandardCharsets.US_ASCII));
    }

    static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 es obligatorio en la plataforma Java", e);
        }
    }
}
