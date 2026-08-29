package com.ohmdevs.hrsaas.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Índice ciego para columnas cifradas (AD-08): HMAC-SHA256 en hexadecimal
 * (64 caracteres) del valor en claro, con una clave separada de la de
 * {@link AesGcmEncryptionService}.
 *
 * <p>El valor cifrado con AES-GCM lleva un IV aleatorio distinto en cada
 * operación (a propósito, ver {@link AesGcmEncryptionService}), así que dos
 * filas con el mismo documento de identidad producen ciphertexts distintos —
 * no se puede indexar ni comparar por igualdad directamente sobre la columna
 * cifrada. El HMAC, en cambio, es determinista: mismo valor en claro, mismo
 * hash siempre, así que sirve como índice único y como filtro de búsqueda
 * exacta sin exponer el dato ni depender del cifrado reversible para ello.
 * Mismo patrón que {@link com.ohmdevs.hrsaas.platform.service.Tokens#sha256Hex(String)}
 * para {@code Invitation.tokenHash}, con HMAC en vez de hash simple porque
 * aquí sí hace falta una clave: sin ella, cualquiera con acceso a la base
 * podría fuerza-bruta un documento de identidad por diccionario (el espacio de
 * cédulas/pasaportes válidos es mucho más pequeño que el de un token
 * aleatorio de 256 bits).
 *
 * <p>Lo calcula la capa de aplicación al construir o actualizar la entidad —
 * nunca el {@code AttributeConverter} del cifrado, que no sabe nada de índices.
 */
@Service
public class HmacBlindIndexService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacBlindIndexService(@Value("${HRSAAS_CRYPTO_HMAC_KEY:}") String base64Key) {
        this.key = new SecretKeySpec(
                AesGcmEncryptionService.decodeKey(base64Key, "HRSAAS_CRYPTO_HMAC_KEY"), ALGORITHM);
    }

    /** Índice ciego en hexadecimal (64 caracteres), o {@code null} si el valor en claro es {@code null}. */
    public String blindIndex(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el índice ciego", e);
        }
    }
}
