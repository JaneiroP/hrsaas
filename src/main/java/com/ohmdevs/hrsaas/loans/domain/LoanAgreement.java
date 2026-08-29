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

/** Préstamo con tabla de amortización ({@link LoanInstallment}). */
@Entity
@Table(name = "loan_agreements")
@Getter
@Setter
public class LoanAgreement extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount;

    @Column(name = "installment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
