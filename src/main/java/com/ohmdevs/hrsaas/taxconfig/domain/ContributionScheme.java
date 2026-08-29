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
 * Régimen de aporte de TSS/INFOTEP (SFS, AFP, SRL, INFOTEP...) vigente por
 * fecha. <strong>Sin {@code tenant_id}</strong> — ver {@code package-info}.
 *
 * <p>{@code capAmount} es el tope ya resuelto a un monto en DOP para esta
 * vigencia (p. ej. "10 salarios mínimos" del blueprint, convertido a la
 * cifra concreta que aplicaba ese día) — este esquema no calcula el tope a
 * partir de otro parámetro, lo recibe ya calculado. Quien carga la
 * configuración es responsable de esa conversión.
 *
 * <p>{@code @NamedInterface} a nivel de clase, no de paquete: {@code payroll}
 * necesita el tipo de retorno real de {@code TaxConfigLookupService.schemeAsOf},
 * no solo el método — mismo patrón que {@code TenantAwareEntity} en
 * {@code platform.domain}.
 */
@org.springframework.modulith.NamedInterface("domain")
@Entity
@Table(name = "contribution_schemes")
@Getter
@Setter
public class ContributionScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "employee_rate", precision = 7, scale = 4)
    private BigDecimal employeeRate;

    @Column(name = "employer_rate", precision = 7, scale = 4)
    private BigDecimal employerRate;

    @Column(name = "cap_amount", precision = 15, scale = 2)
    private BigDecimal capAmount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
