package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code {"schemeCode": "SFS", "ratePerspective": "EMPLOYEE", "base": "GROSS"}}
 * — tasa y tope resueltos en vivo contra {@link ContributionScheme} de
 * {@code taxconfig} (AD-04: ni la tasa ni el tope viven aquí ni en
 * {@code formulaParams}). Si el esquema no tiene tope para esta vigencia
 * ({@code capAmount} nulo), la base no se recorta.
 */
@Component
public class RateOfBaseStrategy implements ConceptCalculationStrategy {

    private final ObjectMapper objectMapper;
    private final TaxConfigLookupService taxConfigLookupService;

    public RateOfBaseStrategy(ObjectMapper objectMapper, TaxConfigLookupService taxConfigLookupService) {
        this.objectMapper = objectMapper;
        this.taxConfigLookupService = taxConfigLookupService;
    }

    @Override
    public FormulaStrategy handles() {
        return FormulaStrategy.RATE_OF_BASE;
    }

    @Override
    public BigDecimal calculate(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate) {
        JsonNode params = objectMapper.readTree(concept.getFormulaParams());
        String schemeCode = params.path("schemeCode").asText();
        String ratePerspective = params.path("ratePerspective").asText();
        String baseKey = params.path("base").asText();

        ContributionScheme scheme = taxConfigLookupService.schemeAsOf(schemeCode, asOfDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Sin ContributionScheme vigente para '%s' en %s".formatted(schemeCode, asOfDate)));

        BigDecimal rate = "EMPLOYER".equals(ratePerspective) ? scheme.getEmployerRate() : scheme.getEmployeeRate();
        BigDecimal base = context.base(baseKey);
        if (scheme.getCapAmount() != null) {
            base = base.min(scheme.getCapAmount());
        }
        return base.multiply(rate);
    }
}
