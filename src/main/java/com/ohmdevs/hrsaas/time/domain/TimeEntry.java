package com.ohmdevs.hrsaas.time.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Marcaje crudo (entrada, salida, inicio/fin de descanso). AD-11: idempotente
 * por {@code (tenant_id, employee_id, device_id, occurred_at)} — un reintento
 * de webhook del reloj biométrico no debe duplicar el marcaje.
 */
@Entity
@Table(name = "time_entries")
@Getter
@Setter
public class TimeEntry extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimeEntryType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimeEntrySource source;

    /** Id del dispositivo de origen; nulo para marcajes {@code MANUAL}. */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
