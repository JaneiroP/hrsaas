package com.ohmdevs.hrsaas.shared.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacBlindIndexServiceTest {

    private final HmacBlindIndexService service = new HmacBlindIndexService(randomBase64Key());

    @Test
    @DisplayName("el mismo valor en claro siempre produce el mismo indice (determinista, a diferencia del cifrado)")
    void deterministic() {
        String value = "001-1234567-8";
        assertThat(service.blindIndex(value)).isEqualTo(service.blindIndex(value));
    }

    @Test
    @DisplayName("el indice tiene 64 caracteres hexadecimales (SHA-256/HMAC-SHA256)")
    void isSixtyFourHexChars() {
        String index = service.blindIndex("001-1234567-8");
        assertThat(index).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("valores distintos producen indices distintos")
    void differentValuesDifferentIndex() {
        assertThat(service.blindIndex("001-1234567-8"))
                .isNotEqualTo(service.blindIndex("001-7654321-0"));
    }

    @Test
    @DisplayName("null pasa a traves sin tocar")
    void nullPassesThrough() {
        assertThat(service.blindIndex(null)).isNull();
    }

    @Test
    @DisplayName("una clave que no decodifica a 32 bytes falla rapido, en el constructor")
    void rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new HmacBlindIndexService(shortKey))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
