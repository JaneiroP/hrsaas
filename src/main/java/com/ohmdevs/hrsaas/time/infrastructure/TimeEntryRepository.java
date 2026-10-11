package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long employeeId, Instant from, Instant to);

    /**
     * Inserta un marcaje de dispositivo si no existe ya; devuelve 1 si lo
     * insertó y 0 si ya estaba.
     *
     * <p>Nativa porque JPA no tiene {@code ON CONFLICT}. Por eso el
     * {@code tenant_id} va explícito: {@code @TenantId} solo rellena la
     * columna en inserciones de entidades. La política de RLS
     * ({@code WITH CHECK}) rechaza la fila si ese valor no es el tenant de la
     * transacción, así que un error de quien llama falla en vez de escribir
     * en otro tenant.
     */
    @Modifying
    @Query(value = """
            INSERT INTO time_entries (tenant_id, employee_id, type, occurred_at, source, device_id)
            VALUES (:tenantId, :employeeId, :type, :occurredAt, 'BIOMETRIC', :deviceId)
            ON CONFLICT ON CONSTRAINT uk_time_entries_device DO NOTHING
            """, nativeQuery = true)
    int insertBiometricIfAbsent(@Param("tenantId") Long tenantId, @Param("employeeId") Long employeeId,
                                @Param("type") String type, @Param("occurredAt") Instant occurredAt,
                                @Param("deviceId") String deviceId);
}
