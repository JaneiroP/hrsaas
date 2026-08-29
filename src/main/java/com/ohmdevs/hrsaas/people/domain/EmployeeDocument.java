package com.ohmdevs.hrsaas.people.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadata de un archivo adjunto al expediente. El archivo en sí vive en
 * S3/MinIO ({@code storageKey}); esta fila nunca guarda el binario.
 */
@Entity
@Table(name = "employee_documents")
@Getter
@Setter
public class EmployeeDocument extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmployeeDocumentCategory category;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Ruta dentro del bucket, layout {@code <tenantId>/employees/<employeeId>/<uuid>-<fileName>}. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;
}
