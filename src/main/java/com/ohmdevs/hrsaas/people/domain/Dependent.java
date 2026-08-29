package com.ohmdevs.hrsaas.people.domain;

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

import java.time.LocalDate;

/**
 * Dependiente de un {@link Employee} (para seguro médico, TSS, etc.).
 *
 * <p>{@code documentNumber} <strong>sin cifrar</strong>, a diferencia de
 * {@code Employee.documentNumber}/{@code BankAccount.accountNumber} — fuera
 * del alcance literal de AD-08. Revisar si el negocio realmente captura
 * cédula de dependientes menores antes de confiar en esta decisión a largo
 * plazo.
 */
@Entity
@Table(name = "dependents")
@Getter
@Setter
public class Dependent extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DependentRelationship relationship;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "document_number", length = 255)
    private String documentNumber;
}
