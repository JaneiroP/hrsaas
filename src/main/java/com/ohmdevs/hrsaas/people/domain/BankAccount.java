package com.ohmdevs.hrsaas.people.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import com.ohmdevs.hrsaas.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Cuenta bancaria para depósito de nómina. {@code accountNumber} cifrado
 * (AD-08), igual que {@code Employee.documentNumber} — mismo motivo: IV
 * aleatorio por operación, unicidad real vía {@code accountNumberHmac}.
 */
@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
public class BankAccount extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "bank_name", nullable = false, length = 160)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private BankAccountType accountType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", nullable = false, length = 255)
    private String accountNumber;

    /**
     * Índice ciego HMAC-SHA256 de {@link #accountNumber}, calculado por la
     * capa de aplicación — mismo motivo que {@code Employee.documentNumberHmac}
     * para {@code @JdbcTypeCode(SqlTypes.CHAR)}: la columna es {@code CHAR(64)}
     * a propósito, y sin el código de tipo explícito (no basta con
     * {@code columnDefinition}, que solo afecta el DDL generado) Hibernate
     * espera {@code VARCHAR} y la validación de esquema falla contra Postgres real.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_number_hmac", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String accountNumberHmac;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
