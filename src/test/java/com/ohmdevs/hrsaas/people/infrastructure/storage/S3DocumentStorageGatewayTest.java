package com.ohmdevs.hrsaas.people.infrastructure.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roundtrip real contra LocalStack, no un mock de {@code S3Client}: lo que
 * este test verifica es que las opciones de configuración (path-style
 * access, endpoint override, credenciales estáticas) realmente producen un
 * cliente S3 funcional — un mock no puede fallar por una mala combinación de
 * esas opciones, que es justo el tipo de error que rompe en producción
 * contra MinIO.
 *
 * <p>Patrón "singleton container" igual que {@code AbstractPostgresTest}: un
 * único contenedor estático, sin {@code @Container}, arrancado a mano si hay
 * Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3DocumentStorageGatewayTest {

    private static final String BUCKET = "hrsaas-documents-test";

    // Version fijada, verificada arrancando el contenedor a mano (`docker logs`
    // mostro "Ready.", sin pedir credenciales): las imagenes recientes de
    // LocalStack (2026.x, versionado por calendario) exigen un
    // LOCALSTACK_AUTH_TOKEN de pago solo para arrancar, incluso para S3 basico
    // -- salen con "License activation failed", codigo de salida 55. 3.8 es de
    // antes de ese cambio de modelo de negocio y sigue siendo gratis.
    @SuppressWarnings("resource")
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
            .withServices("s3");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            LOCALSTACK.start();
        }
    }

    @AfterAll
    static void stopContainer() {
        if (LOCALSTACK.isRunning()) {
            LOCALSTACK.stop();
        }
    }

    @Test
    @DisplayName("subir, bajar y borrar un objeto contra LocalStack")
    void uploadDownloadDelete() {
        createBucket();
        S3DocumentStorageGateway gateway = new S3DocumentStorageGateway(
                BUCKET, LOCALSTACK.getRegion(), LOCALSTACK.getEndpoint().toString(),
                LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey(), true);

        String key = "1/employees/1/test-file.txt";
        byte[] content = "contenido de prueba".getBytes(StandardCharsets.UTF_8);

        gateway.upload(key, content, "text/plain");
        assertThat(gateway.download(key)).isEqualTo(content);

        gateway.delete(key);
        assertThat(objectExists(key)).isFalse();
    }

    /** LocalStack no crea el bucket solo: se hace con el propio SDK antes del test. */
    private void createBucket() {
        try (S3Client admin = adminClient()) {
            admin.createBucket(b -> b.bucket(BUCKET));
        }
    }

    private boolean objectExists(String key) {
        try (S3Client admin = adminClient()) {
            admin.headObject(b -> b.bucket(BUCKET).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private S3Client adminClient() {
        return S3Client.builder()
                .region(Region.of(LOCALSTACK.getRegion()))
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
