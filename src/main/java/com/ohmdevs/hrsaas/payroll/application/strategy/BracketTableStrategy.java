package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code {"schemeCode": "ISR_ASALARIADOS", "base": "ISR_TAXABLE_BASE"}} —
 * tramos progresivos de {@link TaxBracket} en {@code taxconfig}, resueltos
 * en vivo contra la fecha del período (AD-04). Cálculo progresivo estándar:
 * cada tramo aporta su tasa solo sobre la porción de la base que cae dentro
 * de {@code [lowerBound, upperBound)}, nunca sobre el total de la base con
 * la tasa del tramo superior.
 */
@Component
public class BracketTableStrategy implements ConceptCalculationStrategy {

    private final ObjectMapper objectMapper;
    private final TaxConfigLookupService taxConfigLookupService;

    public BracketTableStrategy(ObjectMapper objectMapper, TaxConfigLookupService taxConfigLookupService) {
        this.objectMapper = objectMapper;
        this.taxConfigLookupService = taxConfigLookupService;
    }

    @Override
    public FormulaStrategy handles() {
        return FormulaStrategy.BRACKET_TABLE;
    }

    @Override
    public BigDecimal calculate(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate) {
        JsonNode params = objectMapper.readTree(concept.getFormulaParams());
        String schemeCode = params.path("schemeCode").asText();
        String baseKey = params.path("base").asText();

        List<TaxBracket> brackets = taxConfigLookupService.bracketsAsOf(schemeCode, asOfDate);
        if (brackets.isEmpty()) {
            throw new IllegalStateException(
                    "Sin escala de TaxBracket vigente para '%s' en %s".formatted(schemeCode, asOfDate));
        }

        BigDecimal base = context.base(baseKey);
        BigDecimal total = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets) {
            if (base.compareTo(bracket.getLowerBound()) <= 0) {
                continue;
            }
            BigDecimal upper = bracket.getUpperBound() != null ? bracket.getUpperBound() : base;
            BigDecimal taxableInBracket = base.min(upper).subtract(bracket.getLowerBound());
            if (taxableInBracket.signum() > 0) {
                total = total.add(taxableInBracket.multiply(bracket.getRate()));
            }
        }
        return total;
    }
}
