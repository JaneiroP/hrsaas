package com.ohmdevs.hrsaas.platform.tenancy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Primera capa de aislamiento: le dice a Hibernate qué tenant está activo para
 * que añada el predicado a cada consulta sobre una entidad con {@code @TenantId}.
 *
 * <p>Cuando no hay tenant devuelve {@link #NO_TENANT}, un id que no existe, en
 * vez de {@code null}. La diferencia importa: Hibernate rechaza {@code null} y,
 * más al fondo, un identificador imposible hace que una consulta sin contexto
 * devuelva cero filas en lugar de todas. Falla cerrado.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    /** Identificador imposible: ninguna fila lo tiene, así que no pasa nada sin contexto. */
    public static final Long NO_TENANT = -1L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.current();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    /**
     * {@code false}: no revalidamos sesiones existentes al cambiar de tenant.
     * Cada petición y cada step de Batch abren su propia sesión con su contexto.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
