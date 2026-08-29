package com.ohmdevs.hrsaas.people.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

/**
 * Implementación real de {@link DocumentStorageGateway} sobre S3/MinIO.
 *
 * <p>{@code endpointOverride} vacío significa AWS S3 real; con valor, apunta
 * a MinIO en local o a LocalStack en test — {@code path-style-access} tiene
 * que ir en {@code true} en ambos casos porque ninguno resuelve el estilo
 * "virtual-hosted" (bucket como subdominio) que S3 real sí soporta.
 *
 * <p>Con {@code access-key}/{@code secret-key} vacíos usa
 * {@link DefaultCredentialsProvider} (rol IAM, variables de entorno
 * estándar) — pensado para producción real, donde nunca se fijan credenciales
 * a mano en propiedades.
 */
@Component
public class S3DocumentStorageGateway implements DocumentStorageGateway {

    private final S3Client s3Client;
    private final String bucket;

    public S3DocumentStorageGateway(
            @Value("${hrsaas.storage.s3.bucket:hrsaas-documents}") String bucket,
            @Value("${hrsaas.storage.s3.region:us-east-1}") String region,
            @Value("${hrsaas.storage.s3.endpoint:}") String endpoint,
            @Value("${hrsaas.storage.s3.access-key:}") String accessKey,
            @Value("${hrsaas.storage.s3.secret-key:}") String secretKey,
            @Value("${hrsaas.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        this.bucket = bucket;

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider(accessKey, secretKey))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = builder.build();
    }

    private static AwsCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] download(String key) {
        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return object.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el objeto '" + key + "' del bucket " + bucket, e);
        } catch (S3Exception e) {
            throw new IllegalStateException("No se pudo descargar '" + key + "' del bucket " + bucket, e);
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
