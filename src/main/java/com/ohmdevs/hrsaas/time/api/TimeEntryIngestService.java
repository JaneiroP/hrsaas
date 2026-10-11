package com.ohmdevs.hrsaas.time.api;

import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.domain.TimeEntryType;
import com.ohmdevs.hrsaas.time.infrastructure.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entrada de marcajes externos hacia {@code time}, para {@code integrations}.
 *
 * <p><strong>Idempotente (AD-11).</strong> Quien entrega marcajes reintenta:
 * el mismo lote puede llegar dos veces, entero o a medias. Cada marcaje se
 * inserta con {@code ON CONFLICT DO NOTHING} sobre la restricción única
 * {@code uk_time_entries_device}, así que el segundo intento no crea filas y
 * se cuenta como duplicado.
 *
 * <p>No pasa por {@code TimeTrackingService.recordTimeEntry}, que guarda con
 * JPA: un {@code INSERT} que choca con la restricción única aborta la
 * transacción entera de PostgreSQL, y con ella el resto del lote. Comprobar
 * antes si existe tampoco sirve, porque dos entregas simultáneas pasan las dos
 * la comprobación. La base de datos es la única que puede decidirlo.
 *
 * <p>Los empleados deben existir en el tenant activo: quien llama lo verifica
 * antes, porque {@code time} no depende de {@code people}.
 */
@Service
public class TimeEntryIngestService {

    private final TimeEntryRepository timeEntryRepository;

    public TimeEntryIngestService(TimeEntryRepository timeEntryRepository) {
        this.timeEntryRepository = timeEntryRepository;
    }

    @Transactional
    public PunchIngestResult recordBiometric(List<ExternalPunch> punches) {
        Long tenantId = TenantContext.require();
        int recorded = 0;
        for (ExternalPunch punch : punches) {
            recorded += timeEntryRepository.insertBiometricIfAbsent(tenantId, punch.employeeId(),
                    typeOf(punch.direction()).name(), punch.occurredAt(), punch.deviceId());
        }
        return new PunchIngestResult(recorded, punches.size() - recorded);
    }

    private static TimeEntryType typeOf(ExternalPunch.Direction direction) {
        return switch (direction) {
            case IN -> TimeEntryType.CLOCK_IN;
            case OUT -> TimeEntryType.CLOCK_OUT;
        };
    }
}
