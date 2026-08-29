package com.ohmdevs.hrsaas.contracts.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Registro de desvinculación. Sin {@code @Audited}: es un hecho que ocurre
 * una sola vez, no tiene historial que reconstruir.
 *
 * <p>{@code severanceEligible} solo registra la elegibilidad declarada — el
 * cálculo real de cesantía es Fase 3, cuando exista el motor de nómina.
 */
@Entity
@Table(name = "terminations")
@Getter
@Setter
public class Termination extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false, unique = true)
    private EmploymentContract contract;

    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TerminationCause cause;

    @Column(name = "notice_given", nullable = false)
    private boolean noticeGiven;

    @Column(name = "severance_eligible", nullable = false)
    private boolean severanceEligible;

    @Column(length = 500)
    private String notes;
}
