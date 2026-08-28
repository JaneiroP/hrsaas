package com.ohmdevs.hrsaas.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base de toda entidad que pertenece a un tenant.
 *
 * <p>{@link TenantId} hace que Hibernate añada el predicado de tenant a cada
 * consulta y rellene la columna al insertar, tomando el valor del
 * {@code CurrentTenantIdentifierResolver}. No hay setter a propósito: el tenant
 * de una fila no se cambia, y una entidad tenant-aware nunca mapea una
 * asociación a {@code Tenant} porque ya lleva el id aquí.
 */
@MappedSuperclass
public abstract class TenantAwareEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }
}
