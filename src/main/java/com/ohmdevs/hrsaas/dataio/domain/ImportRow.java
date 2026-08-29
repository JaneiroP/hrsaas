package com.ohmdevs.hrsaas.dataio.domain;

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

/**
 * Una fila cruda de un {@link ImportBatch}, con su número de línea
 * (blueprint §07, paso 2 "staging" — nada toca aún las tablas de negocio).
 * {@code rawData} es el registro CSV completo serializado a JSON
 * ({@code Map<String,String>}); {@code errors} es la lista de
 * {@code com.ohmdevs.hrsaas.dataio.api.RowError} de esta fila, también JSON
 * (nula si {@link #status} es {@code VALID}).
 */
@Entity
@Table(name = "import_rows")
@Getter
@Setter
public class ImportRow extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false, updatable = false)
    private ImportBatch importBatch;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", nullable = false, updatable = false)
    private String rawData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportRowStatus status = ImportRowStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors")
    private String errors;
}
