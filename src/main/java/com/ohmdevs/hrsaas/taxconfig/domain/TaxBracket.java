package com.ohmdevs.hrsaas.taxconfig.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un tramo de una escala progresiva (ISR asalariados, por ejemplo), vigente
 * por fecha. <strong>Sin {@code tenant_id}</strong> — ver {@code package-info}.
 *
 * <p>{@code upperBound} nulo significa "sin tope", el último tramo de la
 * escala. Varios tramos comparten {@code schemeCode} y {@code effectiveFrom}/
 * {@code effectiveTo} — juntos arman la escala completa vigente ese día.
 *
 * <p>{@code @NamedInterface} a nivel de clase, no de paquete: {@code payroll}
 * necesita el tipo de retorno real de {@code TaxConfigLookupService.bracketsAsOf},
 * no solo el método — mismo patrón que {@code TenantAwareEntity} en
 * {@code platform.domain}.
 */
@org.springframework.modulith.NamedInterface("domain")
@Entity
@Table(name = "tax_brackets")
@Getter
@Setter
public class TaxBracket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_code", nullable = false, length = 32)
    private String schemeCode;

    @Column(name = "bracket_order", nullable = false)
    private int bracketOrder;

    @Column(name = "lower_bound", nullable = false, precision = 15, scale = 2)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", precision = 15, scale = 2)
    private BigDecimal upperBound;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
