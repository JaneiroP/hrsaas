package com.ohmdevs.hrsaas.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado de columna AES-256-GCM (AD-08): documento de identidad y número de
 * cuenta bancaria, hoy en {@code people.Employee}/{@code people.BankAccount}.
 *
 * <p>Formato de salida: {@code "v1:" + Base64(iv || ciphertext+tag)}. El IV va
 * al frente porque hace falta de vuelta para descifrar, y no es secreto — su
 * única propiedad necesaria es no repetirse nunca con la misma clave, por eso
 * se genera aleatorio (12 bytes) en cada llamada a {@link #encrypt(String)} en
 * vez de derivarse de algo predecible. El prefijo de versión deja preparada
 * una futura rotación de clave sin tener que adivinar el formato de lo ya
 * cifrado.
 *
 * <p><strong>Limitación conocida y aceptada para esta fase:</strong> sin AAD
 * (additional authenticated data) ligando el ciphertext a la fila propietaria
 * — un {@link jakarta.persistence.AttributeConverter} estándar no tiene acceso
 * a la entidad completa, solo al valor del campo. Revisar si se necesita antes
 * de ir a producción.
 */
@Service
public class AesGcmEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String VERSION_PREFIX = "v1:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptionService(@Value("${HRSAAS_CRYPTO_DATA_KEY:}") String base64Key) {
        this.key = new SecretKeySpec(decodeKey(base64Key, "HRSAAS_CRYPTO_DATA_KEY"), "AES");
    }

    static byte[] decodeKey(String base64Key, String variableName) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "La variable de entorno " + variableName + " no está configurada. "
                            + "El cifrado de columna (AD-08) no puede arrancar sin ella.");
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    variableName + " debe decodificar a exactamente 32 bytes (AES-256); tiene "
                            + decoded.length + ".");
        }
        return decoded;
    }

    /** Cifra en claro; {@code null} pasa a través sin tocar (campo opcional sin valor todavía). */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertextAndTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertextAndTag, 0, combined, iv.length, ciphertextAndTag.length);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    /** Descifra; {@code null} pasa a través sin tocar. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Valor cifrado sin el prefijo de versión esperado (" + VERSION_PREFIX + ")");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertextAndTag = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertextAndTag, 0, ciphertextAndTag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertextAndTag);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el valor (clave incorrecta o dato corrupto)", e);
        }
    }
}
