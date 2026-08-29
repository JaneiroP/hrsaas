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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Acta de devolución de una {@link AssetAssignment}. Entidad propia (no un campo más de la asignación) por el blueprint. */
@Entity
@Table(name = "asset_returns")
@Getter
@Setter
public class AssetReturn extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_assignment_id", nullable = false, updatable = false, unique = true)
    private AssetAssignment assetAssignment;

    @Column(name = "returned_at", nullable = false)
    private Instant returnedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetReturnCondition condition;

    @Column(length = 500)
    private String notes;

    @Column(name = "document_key", length = 500)
    private String documentKey;
}
