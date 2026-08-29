package com.ohmdevs.hrsaas.people.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import com.ohmdevs.hrsaas.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Expediente de la persona. Independiente de {@code org} — no lleva
 * {@code company_id}: a quién pertenece la persona lo decide su contrato
 * ({@code contracts}), no su expediente.
 *
 * <p>{@code documentNumber} va cifrado (AD-08, {@link EncryptedStringConverter}):
 * el IV aleatorio de cada operación hace que dos cédulas iguales produzcan
 * ciphertexts distintos, así que la unicidad real vive en
 * {@code documentNumberHmac} (índice ciego determinista), no en esta columna.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 16)
    private DocumentType documentType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_number", nullable = false, length = 255)
    private String documentNumber;

    /**
     * Índice ciego HMAC-SHA256 de {@link #documentNumber}, calculado por la
     * capa de aplicación. La columna es {@code CHAR(64)} a propósito
     * (siempre exactamente 64 caracteres hexadecimales, ver
     * {@code V6__people_core.sql}) — sin {@code @JdbcTypeCode(SqlTypes.CHAR)}
     * Hibernate espera {@code VARCHAR} (el {@code Types} por defecto para
     * {@code String}, incluso con {@code columnDefinition} puesto: eso solo
     * cambia el DDL generado, no el tipo esperado en validación) y la
     * validación de esquema falla al arrancar contra Postgres real —
     * hallazgo real, no cubierto por ningún test contra Testcontainers.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "document_number_hmac", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String documentNumberHmac;

    @Column(length = 160)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Enlace a {@code platform.User}, si esta persona tiene acceso a la aplicación. Nunca al revés. */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmployeeStatus status = EmployeeStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
