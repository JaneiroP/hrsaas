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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Ledger append-only de cambios que no terminan el contrato (promoción,
 * cambio de cargo, de supervisor...). Sin {@code @Audited}: ya es
 * append-only por diseño, auditar una auditoría no aporta nada.
 */
@Entity
@Table(name = "contract_amendments")
@Getter
@Setter
public class ContractAmendment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private EmploymentContract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "amendment_type", nullable = false, length = 20)
    private AmendmentType amendmentType;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** JSON libre: forma distinta según {@link #amendmentType}, no vale la pena una tabla por tipo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String detail;

    @Column(length = 500)
    private String notes;
}
