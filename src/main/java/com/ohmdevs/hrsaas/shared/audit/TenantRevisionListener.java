package com.ohmdevs.hrsaas.shared.audit;

import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.hibernate.envers.RevisionListener;

/**
 * Rellena {@link TenantRevisionEntity#getTenantId()} en cada revisión nueva.
 *
 * <p>No es un bean de Spring — Envers lo instancia por reflexión (ver
 * {@code @RevisionEntity} en {@link TenantRevisionEntity}), así que
 * {@link TenantContext#current()} (un {@code ThreadLocal} estático) es la
 * única vía para leer el tenant activo aquí. Único punto de {@code shared}
 * que depende de {@code platform :: tenancy}.
 *
 * <p><strong>Asunción no garantizada por el compilador:</strong> cada
 * revisión corresponde a un único tenant, porque cada transacción de negocio
 * corre hoy dentro de un único {@link TenantContext}. Si algún día una
 * transacción tocara más de un tenant, esta clase seguiría asignando el
 * tenant del hilo en el momento de la revisión, no uno por fila auditada.
 */
public class TenantRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((TenantRevisionEntity) revisionEntity).setTenantId(TenantContext.require());
    }
}
