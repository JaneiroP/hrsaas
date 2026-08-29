package com.ohmdevs.hrsaas.loans.domain;

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

/** Una cuota de la tabla de amortización de un {@link LoanAgreement}. Intra-módulo: {@code @ManyToOne} normal. */
@Entity
@Table(name = "loan_installments")
@Getter
@Setter
public class LoanInstallment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_agreement_id", nullable = false, updatable = false)
    private LoanAgreement loanAgreement;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanInstallmentStatus status = LoanInstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;
}
