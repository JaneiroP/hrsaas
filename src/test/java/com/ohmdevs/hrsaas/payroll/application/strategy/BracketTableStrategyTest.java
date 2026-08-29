package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sin base de datos. La escala de tres tramos usada aquí es inventada para
 * el test — no la escala real del ISR dominicano, ver
 * {@code TaxConfigLookupServiceTest}.
 */
class BracketTableStrategyTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TaxConfigLookupService taxConfigLookupService = mock(TaxConfigLookupService.class);
    private final BracketTableStrategy strategy = new BracketTableStrategy(objectMapper, taxConfigLookupService);
    private final LocalDate asOfDate = LocalDate.of(2025, 1, 31);

    @BeforeEach
    void setUpBrackets() {
        TaxBracket first = bracket(1, "0.00", "10000.00", "0.0000");
        TaxBracket second = bracket(2, "10000.00", "20000.00", "0.1500");
        TaxBracket third = bracket(3, "20000.00", null, "0.2000");
        when(taxConfigLookupService.bracketsAsOf("ISR_TEST", asOfDate)).thenReturn(List.of(first, second, third));
    }

    @Test
    void handlesBracketTable() {
        assertThat(strategy.handles()).isEqualTo(FormulaStrategy.BRACKET_TABLE);
    }

    @Test
    void appliesEachBracketOnlyToItsOwnPortionOfTheBase() {
        PayrollCalculationContext context = contextWithTaxableBase("25000.00");
        PayrollConcept concept = concept("{\"schemeCode\": \"ISR_TEST\", \"base\": \"ISR_TAXABLE_BASE\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        // tramo 1: (10000-0)*0 = 0; tramo 2: (20000-10000)*0.15 = 1500; tramo 3: (25000-20000)*0.20 = 1000
        assertThat(amount).isEqualByComparingTo("2500.00");
    }

    @Test
    void aBaseEntirelyInsideTheFirstBracketOwesNothing() {
        PayrollCalculationContext context = contextWithTaxableBase("8000.00");
        PayrollConcept concept = concept("{\"schemeCode\": \"ISR_TEST\", \"base\": \"ISR_TAXABLE_BASE\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        assertThat(amount).isEqualByComparingTo("0.00");
    }

    private PayrollCalculationContext contextWithTaxableBase(String taxableGross) {
        PayrollCalculationContext context = new PayrollCalculationContext(
                new ActiveContractSummary(1L, 1L, 1L, 1L, new BigDecimal(taxableGross), "DOP"),
                new AttendanceSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        // ISR_TAXABLE_BASE = devengos gravables - TSS deducible; sin TSS registrado, queda igual al bruto gravable.
        context.recordEarning(new BigDecimal(taxableGross), true);
        return context;
    }

    private PayrollConcept concept(String formulaParams) {
        PayrollConcept concept = new PayrollConcept();
        concept.setFormulaParams(formulaParams);
        return concept;
    }

    private TaxBracket bracket(int order, String lower, String upper, String rate) {
        TaxBracket bracket = new TaxBracket();
        bracket.setBracketOrder(order);
        bracket.setLowerBound(new BigDecimal(lower));
        bracket.setUpperBound(upper == null ? null : new BigDecimal(upper));
        bracket.setRate(new BigDecimal(rate));
        return bracket;
    }
}
