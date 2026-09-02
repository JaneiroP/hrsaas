package com.ohmdevs.hrsaas.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirma en vivo, no de memoria, que el {@link ObjectMapper} (Jackson 3,
 * {@code tools.jackson}) que autoconfigura Boot serializa {@link LocalDate}
 * como cadena ISO-8601 ({@code "2023-02-03"}), no como el array
 * {@code [2023,2,3]} que produce Jackson por defecto sin
 * {@code WRITE_DATES_AS_TIMESTAMPS=false}.
 *
 * <p>Importa porque todas las respuestas de {@code portal} (primeros
 * controladores REST del proyecto) usan campos {@code LocalDate} con nombre
 * {@code *Iso} y el frontend los trata como cadena — un array rompería el
 * contrato en silencio, sin que ningún test de servicio lo detectara, porque
 * ninguno pasa por serialización JSON real.
 */
@JsonTest
class JsonDateSerializationSpikeTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void localDateSerializesAsIsoString() {
        String json = objectMapper.writeValueAsString(LocalDate.of(2023, 2, 3));
        assertThat(json).isEqualTo("\"2023-02-03\"");
    }
}
