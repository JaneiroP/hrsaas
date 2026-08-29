package com.ohmdevs.hrsaas.payroll.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un concepto de nómina configurable por tenant — "el cliente nuevo paga un
 * bono de transporte no cotizable" es un {@code INSERT}, no un despliegue
 * (blueprint §04).
 *
 * <p>{@code formulaParams} es JSON cuya forma depende de
 * {@link #formulaStrategy}, interpretado por el
 * {@code ConceptCalculationStrategy} correspondiente en
 * {@code payroll.application.strategy}. Salvo {@code FIXED} (un monto propio
 * del tenant, no una cifra fiscal), ninguna estrategia lleva una tasa o
 * multiplicador embebido aquí — todas referencian por código una fila de
 * {@code taxconfig}, resuelta en vivo contra la fecha del período (AD-04:
 * las tasas fiscales viven solo en tablas con vigencia, nunca copiadas a un
 * segundo lugar sin su propia fecha de corte):
 * <ul>
 *   <li>{@code FIXED}: {@code {"amount": "5000.00"}} — monto propio del
 *   tenant (p. ej. un bono fijo), no un valor legal.</li>
 *   <li>{@code RATE_OF_BASE}: {@code {"schemeCode": "SFS", "ratePerspective": "EMPLOYEE", "base": "GROSS"}}
 *   — resuelve tasa y tope contra {@code ContributionScheme} de
 *   {@code taxconfig} por {@code schemeCode}; {@code ratePerspective} es
 *   {@code EMPLOYEE} o {@code EMPLOYER}; {@code base} es {@code GROSS} o
 *   {@code ISR_TAXABLE_BASE} (ver {@code PayrollCalculationContext}).</li>
 *   <li>{@code HOURS_TIMES_RATE}: {@code {"rateMultiplierParameterCode": "RECARGO_HORAS_EXTRA_DIURNAS", "standardMonthlyHoursParameterCode": "HORAS_MES_ESTANDAR"}}
 *   — ambos resueltos contra {@code StatutoryParameter} de
 *   {@code taxconfig}; horas de {@code AttendanceSummary.overtimeHours()}.</li>
 *   <li>{@code BRACKET_TABLE}: {@code {"schemeCode": "ISR_ASALARIADOS", "base": "ISR_TAXABLE_BASE"}}
 *   — tramos de {@code TaxBracket} de {@code taxconfig} por
 *   {@code schemeCode}.</li>
 * </ul>
 */
@Entity
@Table(name = "payroll_concepts")
@Getter
@Setter
public class PayrollConcept extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PayrollConceptType type;

    @Column(name = "execution_order", nullable = false)
    private int executionOrder;

    @Column(name = "taxable_for_isr", nullable = false)
    private boolean taxableForIsr;

    @Column(name = "contributes_to_tss", nullable = false)
    private boolean contributesToTss;

    @Column(name = "affects_regalia", nullable = false)
    private boolean affectsRegalia;

    @Enumerated(EnumType.STRING)
    @Column(name = "formula_strategy", nullable = false, length = 24)
    private FormulaStrategy formulaStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_params", nullable = false)
    private String formulaParams;

    @Column(nullable = false)
    private boolean active = true;
}
