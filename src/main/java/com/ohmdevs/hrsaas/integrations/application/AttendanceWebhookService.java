package com.ohmdevs.hrsaas.integrations.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ohmdevs.hrsaas.integrations.domain.AttendanceConnector;
import com.ohmdevs.hrsaas.integrations.infrastructure.AttendanceConnectorRepository;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectoryQueryService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.api.ExternalPunch;
import com.ohmdevs.hrsaas.time.api.PunchIngestResult;
import com.ohmdevs.hrsaas.time.api.TimeEntryIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recibe un lote de marcajes de un sistema de asistencia y lo entrega a
 * {@code time}.
 *
 * <p><strong>Primero se autentica, después se lee.</strong> El cuerpo no se
 * interpreta hasta que la firma coincide: un JSON que nadie firmó no llega
 * nunca al deserializador.
 *
 * <p><strong>Un evento que no se puede usar se omite, no tumba el lote.</strong>
 * Quien envía reintenta el mismo lote ante cualquier respuesta que no sea 2xx
 * y, si sigue fallando, deja de enviar. Rechazar cien marcajes porque uno
 * nombra a un empleado que aquí no existe detendría la asistencia de toda la
 * empresa hasta que alguien interviniera. Los omitidos se cuentan en la
 * respuesta y quedan en el log.
 *
 * <p><strong>El empleado llega como id de este sistema.</strong> El emisor ya
 * traduce el código del reloj a un identificador de empleado; el acuerdo es
 * que ese identificador sea el {@code id} del empleado aquí. No hay tabla de
 * correspondencias propia porque sería la segunda para lo mismo.
 */
@Service
public class AttendanceWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceWebhookService.class);
    private static final int MAX_DEVICE_ID_LENGTH = 100;

    private final AttendanceConnectorRepository connectors;
    private final EmployeeDirectoryQueryService employees;
    private final TimeEntryIngestService timeEntries;
    private final ObjectMapper objectMapper;

    public AttendanceWebhookService(AttendanceConnectorRepository connectors, EmployeeDirectoryQueryService employees,
                                    TimeEntryIngestService timeEntries, ObjectMapper objectMapper) {
        this.connectors = connectors;
        this.employees = employees;
        this.timeEntries = timeEntries;
        this.objectMapper = objectMapper;
    }

    /**
     * @param received   eventos que traía el lote
     * @param recorded   marcajes guardados por primera vez
     * @param duplicates marcajes que ya estaban guardados
     * @param skipped    eventos omitidos: empleado desconocido en este tenant o datos inutilizables
     */
    public record BatchResult(int received, int recorded, int duplicates, int skipped) {
    }

    /** Lo que se usa de cada evento; el emisor puede añadir campos sin romper esto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PunchEvent(String employeeId, String deviceId, Instant punchedAtUtc, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(List<PunchEvent> events) {
    }

    /**
     * Debe llamarse con el tenant de la URL ya puesto en {@link TenantContext}:
     * es lo que hace que el conector, los empleados y los marcajes se busquen
     * y se escriban bajo ese tenant y ningún otro.
     *
     * @throws WebhookRejectedException si la petición no se puede autenticar
     * @throws IllegalArgumentException si el cuerpo, ya autenticado, no es un lote de eventos
     */
    @Transactional
    public BatchResult receive(UUID connectorPublicId, String signatureHeader, byte[] body) {
        Long tenantId = TenantContext.require();
        AttendanceConnector connector = connectors.findByPublicId(connectorPublicId)
                .filter(AttendanceConnector::canReceive)
                .orElseThrow(() -> new WebhookRejectedException("Conector " + connectorPublicId
                        + " inexistente o sin secreto en el tenant " + tenantId));
        if (!WebhookSignatureVerifier.isValid(connector.getSigningSecret(), signatureHeader, body, Instant.now())) {
            throw new WebhookRejectedException("Firma inválida o vencida para el conector " + connectorPublicId);
        }

        List<PunchEvent> events = parse(body);
        Map<Long, Boolean> knownEmployees = new HashMap<>();
        List<ExternalPunch> punches = new ArrayList<>(events.size());
        for (PunchEvent event : events) {
            ExternalPunch punch = toPunch(event, knownEmployees);
            if (punch != null) {
                punches.add(punch);
            }
        }
        PunchIngestResult result = timeEntries.recordBiometric(punches);
        connector.setLastReceivedAt(Instant.now());

        int skipped = events.size() - punches.size();
        if (skipped > 0) {
            log.warn("Conector de asistencia {}: {} de {} eventos omitidos (empleado desconocido o datos inutilizables)",
                    connectorPublicId, skipped, events.size());
        }
        return new BatchResult(events.size(), result.recorded(), result.duplicates(), skipped);
    }

    private List<PunchEvent> parse(byte[] body) {
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(body, Envelope.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("El cuerpo no es un lote de eventos de asistencia");
        }
        if (envelope == null || envelope.events() == null) {
            throw new IllegalArgumentException("El cuerpo no trae la lista 'events'");
        }
        return envelope.events();
    }

    /** {@code null} si el evento no se puede convertir en un marcaje de este tenant. */
    private ExternalPunch toPunch(PunchEvent event, Map<Long, Boolean> knownEmployees) {
        if (event == null || event.punchedAtUtc() == null || event.deviceId() == null
                || event.deviceId().isBlank() || event.deviceId().length() > MAX_DEVICE_ID_LENGTH) {
            return null;
        }
        ExternalPunch.Direction direction = directionOf(event.type());
        Long employeeId = employeeIdOf(event.employeeId());
        if (direction == null || employeeId == null) {
            return null;
        }
        // Un empleado de otro tenant es, visto desde aquí, uno que no existe:
        // la consulta corre bajo RLS y no lo devuelve.
        boolean known = knownEmployees.computeIfAbsent(employeeId, id -> employees.findById(id).isPresent());
        return known ? new ExternalPunch(employeeId, direction, event.punchedAtUtc(), event.deviceId()) : null;
    }

    private static ExternalPunch.Direction directionOf(String type) {
        if ("IN".equals(type)) {
            return ExternalPunch.Direction.IN;
        }
        return "OUT".equals(type) ? ExternalPunch.Direction.OUT : null;
    }

    private static Long employeeIdOf(String employeeId) {
        if (employeeId == null || !employeeId.matches("\\d{1,18}")) {
            return null;
        }
        return Long.valueOf(employeeId);
    }
}
