package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sin base de datos: {@link TaxConfigLookupService} va mockeado — la tasa y
 * el tope de este test son valores de referencia inventados para el test,
 * no cifras reales de SFS/AFP, exactamente como documenta
 * {@code TaxConfigLookupServiceTest}.
 */
class RateOfBaseStrategyTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TaxConfigLookupService taxConfigLookupService = mock(TaxConfigLookupService.class);
    private final RateOfBaseStrategy strategy = new RateOfBaseStrategy(objectMapper, taxConfigLookupService);
    private final LocalDate asOfDate = LocalDate.of(2025, 1, 31);

    private ContributionScheme scheme;

    @BeforeEach
    void setUpScheme() {
        scheme = new ContributionScheme();
        scheme.setCode("SFS");
        scheme.setEmployeeRate(new BigDecimal("0.0304"));
        scheme.setEmployerRate(new BigDecimal("0.0709"));
        scheme.setCapAmount(new BigDecimal("100000.00"));
        when(taxConfigLookupService.schemeAsOf("SFS", asOfDate)).thenReturn(Optional.of(scheme));
    }

    @Test
    void handlesRateOfBase() {
        assertThat(strategy.handles()).isEqualTo(FormulaStrategy.RATE_OF_BASE);
    }

    @Test
    void appliesTheEmployeeRateToTheGrossBaseWhenUnderTheCap() {
        PayrollCalculationContext context = contextWithGross("50000.00");
        PayrollConcept concept = concept("{\"schemeCode\": \"SFS\", \"ratePerspective\": \"EMPLOYEE\", \"base\": \"GROSS\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        assertThat(amount).isEqualByComparingTo("1520.00"); // 50000 * 0.0304
    }

    @Test
    void appliesTheEmployerRateWhenAskedForTheEmployerPerspective() {
        PayrollCalculationContext context = contextWithGross("50000.00");
        PayrollConcept concept = concept("{\"schemeCode\": \"SFS\", \"ratePerspective\": \"EMPLOYER\", \"base\": \"GROSS\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        assertThat(amount).isEqualByComparingTo("3545.00"); // 50000 * 0.0709
    }

    @Test
    void clampsTheBaseToTheSchemeCapBeforeApplyingTheRate() {
        PayrollCalculationContext context = contextWithGross("200000.00");
        PayrollConcept concept = concept("{\"schemeCode\": \"SFS\", \"ratePerspective\": \"EMPLOYEE\", \"base\": \"GROSS\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        assertThat(amount).isEqualByComparingTo("3040.00"); // min(200000, 100000) * 0.0304
    }

    private PayrollCalculationContext contextWithGross(String gross) {
        PayrollCalculationContext context = new PayrollCalculationContext(
                new ActiveContractSummary(1L, 1L, 1L, 1L, new BigDecimal(gross), "DOP"),
                new AttendanceSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        context.recordEarning(new BigDecimal(gross), true);
        return context;
    }

    private PayrollConcept concept(String formulaParams) {
        PayrollConcept concept = new PayrollConcept();
        concept.setFormulaParams(formulaParams);
        return concept;
    }
}
