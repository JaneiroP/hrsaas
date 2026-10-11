package com.ohmdevs.hrsaas.integrations.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    // Vector publicado en el contrato del emisor (docs/webhooks.md de
    // attendance-gateway): la firma del cuerpo {"events":[]} con el secreto
    // whsec_test y t=1790000000. Si este test falla, las dos implementaciones
    // dejaron de entenderse, pase lo que pase con los demás tests.
    private static final String SECRET = "whsec_test";
    private static final long SIGNED_AT = 1_790_000_000L;
    private static final byte[] BODY = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
    private static final String HEADER =
            "t=1790000000,v1=81bae186ea58f8c1d7197e1a4277f22f94b00e36734942329ca96618ba29ca1a";

    private static final Instant JUST_AFTER = Instant.ofEpochSecond(SIGNED_AT + 2);

    @Test
    @DisplayName("acepta la firma del vector publicado por el emisor")
    void acceptsThePublishedVector() {
        assertThat(WebhookSignatureVerifier.isValid(SECRET, HEADER, BODY, JUST_AFTER)).isTrue();
    }

    @Test
    @DisplayName("rechaza si cambia el cuerpo, el secreto o la marca de tiempo firmada")
    void rejectsAnythingThatWasNotSigned() {
        byte[] otherBody = "{\"events\":[ ]}".getBytes(StandardCharsets.UTF_8);
        String otherTimestamp = HEADER.replace("t=1790000000", "t=1790000001");

        assertThat(WebhookSignatureVerifier.isValid(SECRET, HEADER, otherBody, JUST_AFTER)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid("whsec_other", HEADER, BODY, JUST_AFTER)).isFalse();
        assertThat(WebhookSignatureVerifier.isValid(SECRET, otherTimestamp, BODY, JUST_AFTER)).isFalse();
    }

    @Test
    @DisplayName("rechaza una firma válida que llega fuera del margen de tiempo, antes o después")
    void rejectsAReplayedRequest() {
        long tolerance = WebhookSignatureVerifier.TOLERANCE.toSeconds();

        assertThat(WebhookSignatureVerifier.isValid(SECRET, HEADER, BODY,
                Instant.ofEpochSecond(SIGNED_AT + tolerance))).isTrue();
        assertThat(WebhookSignatureVerifier.isValid(SECRET, HEADER, BODY,
                Instant.ofEpochSecond(SIGNED_AT + tolerance + 1))).isFalse();
        assertThat(WebhookSignatureVerifier.isValid(SECRET, HEADER, BODY,
                Instant.ofEpochSecond(SIGNED_AT - tolerance - 1))).isFalse();
    }

    @Test
    @DisplayName("rechaza cabeceras ausentes o mal formadas y conectores sin secreto, sin lanzar")
    void rejectsMalformedInput() {
        for (String header : new String[] {null, "", "v1=abc", "t=1790000000", "t=abc,v1=" + "0".repeat(64),
                "t=1790000000,v1=" + "0".repeat(63), HEADER.toUpperCase(), HEADER + ",v2=x"}) {
            assertThat(WebhookSignatureVerifier.isValid(SECRET, header, BODY, JUST_AFTER)).as(String.valueOf(header))
                    .isFalse();
        }
        assertThat(WebhookSignatureVerifier.isValid(null, HEADER, BODY, JUST_AFTER)).isFalse();
    }
}
