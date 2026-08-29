package com.ohmdevs.hrsaas.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionMapping;

/**
 * Revisión de Envers (AD-07) con tenant. {@code REVINFO} es global y sin
 * tenant por defecto — sería la fuga de aislamiento por la puerta de atrás
 * que AD-07 busca cerrar: sin esta columna, el histórico de cambios de
 * cualquier tenant sería indistinguible del de otro.
 *
 * <p><strong>Extiende {@link RevisionMapping}, no {@code DefaultRevisionEntity}.</strong>
 * En Hibernate 7, {@code DefaultRevisionEntity} es {@code final} — no se
 * puede heredar de ella (error de compilación real, no una suposición).
 * {@code RevisionMapping} es la clase base sin sellar de la que
 * {@code DefaultRevisionEntity} a su vez hereda, con el mismo {@code id}
 * (tipo {@code int}, columna física también llamada {@code id} — no
 * {@code rev}, aunque las tablas {@code _aud} sí usan {@code rev} como
 * nombre de su FK hacia aquí) y {@code timestamp}, ya anotados — confirmado
 * por decompilación del jar de {@code hibernate-envers:7.4.5.Final} y por el
 * spike {@code EnversSchemaSpikeTest} antes de escribir
 * {@code V10__contracts_envers.sql}.
 *
 * <p>{@code @GeneratedValue} sin estrategia explícita en {@code id}
 * ({@code AUTO}) resuelve, contra PostgreSQL, a una secuencia gestionada por
 * la aplicación ({@code revinfo_seq}, incremento 50 — el "hi/lo" por defecto
 * de Hibernate) que inserta el valor ya calculado; la columna no lleva
 * {@code IDENTITY} ni {@code DEFAULT} a nivel de base de datos. Verificado
 * con el spike, no adivinado — la primera hipótesis de trabajo (identidad)
 * era incorrecta.
 *
 * <p><strong>{@code tenant_id} no llega gratis a las tablas {@code _aud}.</strong>
 * Este campo sí queda en {@code revinfo} (verificado), pero Envers no audita
 * el campo {@code @TenantId} de las entidades auditadas — el spike lo
 * confirmó ausente en {@code employment_contracts_aud}/{@code salary_history_aud}.
 * El aislamiento de esas dos tablas se resuelve en
 * {@code V11__contracts_envers_rls.sql} vía {@code JOIN} contra esta tabla,
 * no con una columna propia.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(TenantRevisionListener.class)
public class TenantRevisionEntity extends RevisionMapping {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
