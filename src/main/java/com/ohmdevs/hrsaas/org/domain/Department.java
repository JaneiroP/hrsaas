package com.ohmdevs.hrsaas.org.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Departamento de una {@link Company}.
 *
 * <p>Plano en Fase 1, deliberadamente sin jerarquía padre/hijo — el negocio
 * no la ha pedido todavía y añadirla después es un cambio aditivo
 * (una columna {@code parent_id} nullable), no uno disruptivo.
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
public class Department extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
