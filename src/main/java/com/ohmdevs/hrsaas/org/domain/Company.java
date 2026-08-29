package com.ohmdevs.hrsaas.org.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Razón social dentro de un tenant. Un tenant puede tener varias
 * ({@code holding} con varias empresas registradas ante la DGII), aunque
 * Fase 1 no valida ese escenario más allá de permitir varias filas.
 *
 * <p>Sin validación de dígito verificador sobre {@code rnc}: pendiente de
 * confirmar el algoritmo con un contador dominicano.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(nullable = false, length = 11)
    private String rnc;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
