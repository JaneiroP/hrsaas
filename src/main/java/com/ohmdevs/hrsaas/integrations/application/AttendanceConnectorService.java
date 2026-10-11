package com.ohmdevs.hrsaas.integrations.application;

import com.ohmdevs.hrsaas.integrations.domain.AttendanceConnector;
import com.ohmdevs.hrsaas.integrations.infrastructure.AttendanceConnectorRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Alta y mantenimiento de los conectores de asistencia de un tenant.
 *
 * <p>El orden natural de configuración tiene tres pasos, porque cada sistema
 * necesita algo que solo el otro puede darle: se crea el conector aquí (sale
 * la URL), se registra esa URL en el sistema de asistencia (sale el secreto),
 * y se guarda el secreto aquí. Entre el segundo y el tercer paso las entregas
 * se rechazan; el emisor las reintenta y nada se pierde.
 */
@Service
public class AttendanceConnectorService {

    private final AttendanceConnectorRepository connectors;

    public AttendanceConnectorService(AttendanceConnectorRepository connectors) {
        this.connectors = connectors;
    }

    @Transactional
    public AttendanceConnector create(String name) {
        TenantContext.require();
        AttendanceConnector connector = new AttendanceConnector();
        connector.setName(name);
        return connectors.save(connector);
    }

    @Transactional(readOnly = true)
    public List<AttendanceConnector> list() {
        TenantContext.require();
        return connectors.findAllByOrderByCreatedAtAsc();
    }

    /** Sustituye el secreto. Las peticiones firmadas con el anterior dejan de aceptarse de inmediato. */
    @Transactional
    public AttendanceConnector replaceSigningSecret(UUID publicId, String signingSecret) {
        TenantContext.require();
        AttendanceConnector connector = require(publicId);
        connector.setSigningSecret(signingSecret);
        return connector;
    }

    @Transactional
    public void delete(UUID publicId) {
        TenantContext.require();
        connectors.delete(require(publicId));
    }

    private AttendanceConnector require(UUID publicId) {
        return connectors.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Conector de asistencia no encontrado: " + publicId));
    }
}
