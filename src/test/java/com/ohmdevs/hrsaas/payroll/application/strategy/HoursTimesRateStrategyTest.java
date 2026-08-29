package com.ohmdevs.hrsaas.payroll.application.strategy;

import com.ohmdevs.hrsaas.contracts.api.ActiveContractSummary;
import com.ohmdevs.hrsaas.payroll.application.PayrollCalculationContext;
import com.ohmdevs.hrsaas.payroll.domain.FormulaStrategy;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConcept;
import com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService;
import com.ohmdevs.hrsaas.time.api.AttendanceSummary;
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
 * Sin base de datos. El multiplicador de recargo y las horas mensuales
 * estándar son valores de referencia inventados para el test, no cifras del
 * Código de Trabajo validadas — ver {@code TaxConfigLookupServiceTest}.
 */
class HoursTimesRateStrategyTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TaxConfigLookupService taxConfigLookupService = mock(TaxConfigLookupService.class);
    private final HoursTimesRateStrategy strategy = new HoursTimesRateStrategy(objectMapper, taxConfigLookupService);
    private final LocalDate asOfDate = LocalDate.of(2025, 1, 31);

    @Test
    void handlesHoursTimesRate() {
        assertThat(strategy.handles()).isEqualTo(FormulaStrategy.HOURS_TIMES_RATE);
    }

    @Test
    void multipliesTheHourlyRateByTheOvertimeMultiplierAndTheOvertimeHours() {
        when(taxConfigLookupService.parameterAsOf("RECARGO_HORAS_EXTRA_DIURNAS", asOfDate))
                .thenReturn(Optional.of(new BigDecimal("1.35")));
        when(taxConfigLookupService.parameterAsOf("HORAS_MES_ESTANDAR", asOfDate))
                .thenReturn(Optional.of(new BigDecimal("195")));

        PayrollCalculationContext context = new PayrollCalculationContext(
                new ActiveContractSummary(1L, 1L, 1L, 1L, new BigDecimal("39000.00"), "DOP"),
                new AttendanceSummary(BigDecimal.ZERO, new BigDecimal("4"), BigDecimal.ZERO));

        PayrollConcept concept = new PayrollConcept();
        concept.setFormulaParams("{\"rateMultiplierParameterCode\": \"RECARGO_HORAS_EXTRA_DIURNAS\", "
                + "\"standardMonthlyHoursParameterCode\": \"HORAS_MES_ESTANDAR\"}");

        BigDecimal amount = strategy.calculate(concept, context, asOfDate);

        // (39000 / 195) * 1.35 * 4 = 200 * 1.35 * 4 = 1080.00
        assertThat(amount).isEqualByComparingTo("1080.00");
    }
}
