package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * {@code {"rateMultiplierParameterCode": "...", "standardMonthlyHoursParameterCode": "..."}}
 * — horas de {@code AttendanceSummary.overtimeHours()}, tarifa por hora
 * derivada del salario del contrato dividido entre las horas mensuales
 * estándar, ambas resueltas contra {@code StatutoryParameter} de
 * {@code taxconfig} (AD-04: ni el recargo ni el divisor de horas se asumen
 * en código).
 */
@Component
public class HoursTimesRateStrategy implements ConceptCalculationStrategy {

    private final ObjectMapper objectMapper;
    private final TaxConfigLookupService taxConfigLookupService;

    public HoursTimesRateStrategy(ObjectMapper objectMapper, TaxConfigLookupService taxConfigLookupService) {
        this.objectMapper = objectMapper;
        this.taxConfigLookupService = taxConfigLookupService;
    }

    @Override
    public FormulaStrategy handles() {
        return FormulaStrategy.HOURS_TIMES_RATE;
    }

    @Override
    public BigDecimal calculate(PayrollConcept concept, PayrollCalculationContext context, LocalDate asOfDate) {
        JsonNode params = objectMapper.readTree(concept.getFormulaParams());
        String rateMultiplierCode = params.path("rateMultiplierParameterCode").asText();
        String standardHoursCode = params.path("standardMonthlyHoursParameterCode").asText();

        BigDecimal rateMultiplier = taxConfigLookupService.parameterAsOf(rateMultiplierCode, asOfDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Sin StatutoryParameter vigente para '%s' en %s".formatted(rateMultiplierCode, asOfDate)));
        BigDecimal standardMonthlyHours = taxConfigLookupService.parameterAsOf(standardHoursCode, asOfDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Sin StatutoryParameter vigente para '%s' en %s".formatted(standardHoursCode, asOfDate)));

        BigDecimal hourlyRate = context.contract().currentSalary()
                .divide(standardMonthlyHours, 6, RoundingMode.HALF_UP);
        BigDecimal hours = context.attendance().overtimeHours();

        return hourlyRate.multiply(rateMultiplier).multiply(hours);
    }
}
