package com.ohmdevs.hrsaas.time.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Solicitud de ausencia con flujo de aprobación. */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
public class LeaveRequest extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false, updatable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Días hábiles solicitados; los calcula la capa de aplicación contra el calendario de feriados. */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal days;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
