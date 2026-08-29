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
 *
 * <p>{@code @NamedInterface} a nivel de clase, no de paquete: todo módulo de
 * negocio (org, people, contracts, ...) necesita extender esta clase, pero
 * el resto de {@code platform.domain} (Tenant, User, Role, Invitation, ...)
 * sigue interno. Spring Modulith permite anotar un tipo suelto exactamente
 * para este caso — abrir el paquete completo habría expuesto de paso las
 * entidades propias de {@code platform} que ningún otro módulo debe importar.
 */
@org.springframework.modulith.NamedInterface("domain")
@MappedSuperclass
public abstract class TenantAwareEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }
}
