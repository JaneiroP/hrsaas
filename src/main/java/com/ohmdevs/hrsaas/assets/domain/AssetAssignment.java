package com.ohmdevs.hrsaas.assets.domain;

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

/** Entrega de un {@link Asset} a un empleado, con el acta como documento archivado. */
@Entity
@Table(name = "asset_assignments")
@Getter
@Setter
public class AssetAssignment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "returned_at")
    private Instant returnedAt;

    /** Clave del acta firmada en el mismo almacenamiento que {@code EmployeeDocument}; sin FK a {@code people}. */
    @Column(name = "acknowledgement_document_key", length = 500)
    private String acknowledgementDocumentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetAssignmentStatus status = AssetAssignmentStatus.ACTIVE;
}
