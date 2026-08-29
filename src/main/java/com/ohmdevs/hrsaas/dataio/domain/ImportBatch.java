package com.ohmdevs.hrsaas.dataio.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Un archivo subido para importar (blueprint §07). {@code fileHash} es el
 * SHA-256 del archivo crudo, para idempotencia — subir el mismo archivo dos
 * veces no crea un segundo lote.
 */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
public class ImportBatch extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_type", nullable = false, length = 40)
    private String importType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** SHA-256 en hexadecimal: siempre 64 caracteres — {@code CHAR(64)} explícito, ver {@code Employee.documentNumberHmac} para el porqué. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportBatchStatus status = ImportBatchStatus.STAGED;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private Long uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "committed_at")
    private Instant committedAt;
}
