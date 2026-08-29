package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code {"amount": "5000.00"}} — un monto propio del tenant (no una tasa o
 * cifra fiscal, ver {@link PayrollConcept}), tomado tal cual.
 */
@Component
public class FixedAmountStrategy implements ConceptCalculationStrategy {

    private final ObjectMapper objectMapper;

    public FixedAmountStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public FormulaStrategy handles() {
        return FormulaStrategy.FIXED;
    }

    @Override
    public BigDecimal calculate(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate) {
        JsonNode params = objectMapper.readTree(concept.getFormulaParams());
        return new BigDecimal(params.path("amount").asText());
    }
}
