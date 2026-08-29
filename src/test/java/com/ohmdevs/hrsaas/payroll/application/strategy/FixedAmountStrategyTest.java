package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin base de datos: {@code FIXED} solo lee {@code formulaParams}, no consulta {@code taxconfig}. */
class FixedAmountStrategyTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final FixedAmountStrategy strategy = new FixedAmountStrategy(objectMapper);

    @Test
    void handlesFixed() {
        assertThat(strategy.handles()).isEqualTo(FormulaStrategy.FIXED);
    }

    @Test
    void returnsTheConfiguredAmountVerbatim() {
        PayrollConcept concept = new PayrollConcept();
        concept.setFormulaParams("{\"amount\": \"5000.00\"}");

        PayrollCalculationContext context = new PayrollCalculationContext(
                new ActiveContractSummary(1L, 1L, 1L, new BigDecimal("40000.00"), "DOP"),
                new AttendanceSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        BigDecimal amount = strategy.calculate(concept, context, LocalDate.of(2025, 1, 31));

        assertThat(amount).isEqualByComparingTo("5000.00");
    }
}
