package com.ohmdevs.hrsaas.loans.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Avance de salario: un solo desembolso a descontar de una vez en una corrida futura. */
@Entity
@Table(name = "salary_advances")
@Getter
@Setter
public class SalaryAdvance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SalaryAdvanceStatus status = SalaryAdvanceStatus.PENDING;

    @Column(name = "deduction_date")
    private LocalDate deductionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
