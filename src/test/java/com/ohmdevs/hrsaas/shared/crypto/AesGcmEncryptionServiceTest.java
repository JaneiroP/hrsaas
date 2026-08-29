package com.ohmdevs.hrsaas.shared.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sin Spring context: instancia el servicio a mano con una clave de prueba
 * generada aquí mismo, distinta de la que usa {@code src/test/resources}
 * para el resto de la suite — así el test no depende de esa configuración.
 */
class AesGcmEncryptionServiceTest {

    private static final String KEY = randomBase64Key();

    private final AesGcmEncryptionService service = new AesGcmEncryptionService(KEY);

    @Test
    @DisplayName("descifrar lo que se cifró devuelve el valor original")
    void roundtrip() {
        String plaintext = "001-1234567-8";
        String ciphertext = service.encrypt(plaintext);

        assertThat(ciphertext).startsWith("v1:").isNotEqualTo(plaintext);
        assertThat(service.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("cifrar el mismo valor dos veces produce ciphertexts distintos (IV aleatorio)")
    void differentCiphertextEachTime() {
        String plaintext = "001-1234567-8";
        assertThat(service.encrypt(plaintext)).isNotEqualTo(service.encrypt(plaintext));
    }

    @Test
    @DisplayName("null pasa a traves sin tocar, en cifrado y en descifrado")
    void nullPassesThrough() {
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("descifrar con la clave equivocada falla, no devuelve basura")
    void wrongKeyFailsDecryption() {
        String ciphertext = service.encrypt("001-1234567-8");
        AesGcmEncryptionService otherService = new AesGcmEncryptionService(randomBase64Key());

        assertThatThrownBy(() -> otherService.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("una clave que no decodifica a 32 bytes falla rapido, en el constructor")
    void rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmEncryptionService(shortKey))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
