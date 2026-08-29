package com.ohmdevs.hrsaas.taxconfig.api;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import com.ohmdevs.hrsaas.taxconfig.domain.StatutoryParameter;
import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.ContributionSchemeRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.StatutoryParameterRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.TaxBracketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El invariante central de AD-04: "¿qué aplicaba el {@code date}?", no
 * "¿cuál es la escala actual?". Los valores de este test son los de
 * referencia del blueprint (§05) — <strong>no validados por un contador,
 * no aptos para nómina real</strong> — solo sirven para probar que el
 * mecanismo de vigencia por fecha resuelve la fila correcta, incluida la
 * transición entre dos vigencias sucesivas del mismo código.
 *
 * <p>{@code @Transactional} a nivel de clase, no por método suelto:
 * {@code taxconfig} es la única tabla de negocio sin {@code tenant_id}
 * (ver {@code package-info}), así que no hay un tenant nuevo por test que
 * aísle los datos sembrados como en el resto de la suite — sin envolver
 * {@code @BeforeEach} + {@code @Test} en una única transacción que se
 * revierte al final, el código {@code "SFS"} sembrado por un test colisiona
 * con el del siguiente contra {@code uk_contribution_schemes_open}.
 * Encontrado corriendo la clase completa, no un test aislado.
 */
@Transactional
class TaxConfigLookupServiceTest extends AbstractPostgresTest {

    @Autowired
    private ContributionSchemeRepository contributionSchemeRepository;
    @Autowired
    private TaxBracketRepository taxBracketRepository;
    @Autowired
    private StatutoryParameterRepository statutoryParameterRepository;
    @Autowired
    private TaxConfigLookupService lookupService;

    @BeforeEach
    void seedTwoVigenciesOfSfs() {
        // SFS vigente desde 2020-01-01 hasta el dia antes de un cambio de tasa
        // ficticio el 2025-01-01 -- exactamente el escenario que AD-04 exige
        // poder responder: "cual aplicaba antes del cambio" vs "cual aplica ahora".
        ContributionScheme before = new ContributionScheme();
        before.setCode("SFS");
        before.setName("Seguro Familiar de Salud");
        before.setEmployeeRate(new BigDecimal("0.0300"));
        before.setEmployerRate(new BigDecimal("0.0700"));
        before.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        before.setEffectiveTo(LocalDate.of(2024, 12, 31));
        contributionSchemeRepository.save(before);

        ContributionScheme current = new ContributionScheme();
        current.setCode("SFS");
        current.setName("Seguro Familiar de Salud");
        current.setEmployeeRate(new BigDecimal("0.0304"));
        current.setEmployerRate(new BigDecimal("0.0709"));
        current.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        contributionSchemeRepository.save(current);
    }

    @Test
    @DisplayName("resuelve la tasa vigente antes y despues de un cambio, sin confundirlas")
    void resolvesTheSchemeActiveOnEachDate() {
        var beforeChange = lookupService.schemeAsOf("SFS", LocalDate.of(2024, 6, 1));
        assertThat(beforeChange).isPresent();
        assertThat(beforeChange.get().getEmployeeRate()).isEqualByComparingTo("0.0300");

        var afterChange = lookupService.schemeAsOf("SFS", LocalDate.of(2025, 6, 1));
        assertThat(afterChange).isPresent();
        assertThat(afterChange.get().getEmployeeRate()).isEqualByComparingTo("0.0304");

        var beforeAnythingExisted = lookupService.schemeAsOf("SFS", LocalDate.of(2010, 1, 1));
        assertThat(beforeAnythingExisted).isEmpty();
    }

    @Test
    @DisplayName("una escala de tramos (ISR) se resuelve completa y en orden para una fecha")
    void resolvesFullBracketScaleInOrder() {
        saveBracket("ISR_ASALARIADOS", 1, "0.00", "416220.00", "0.0000", LocalDate.of(2025, 1, 1));
        saveBracket("ISR_ASALARIADOS", 2, "416220.01", "624329.00", "0.1500", LocalDate.of(2025, 1, 1));
        saveBracket("ISR_ASALARIADOS", 3, "624329.01", null, "0.2500", LocalDate.of(2025, 1, 1));

        List<TaxBracket> brackets = lookupService.bracketsAsOf("ISR_ASALARIADOS", LocalDate.of(2025, 6, 1));

        assertThat(brackets).hasSize(3);
        assertThat(brackets).extracting(TaxBracket::getBracketOrder).containsExactly(1, 2, 3);
        assertThat(brackets.get(2).getUpperBound()).as("el ultimo tramo no tiene tope").isNull();
    }

    @Test
    @DisplayName("un parametro estatutario se resuelve por fecha igual que un esquema de contribucion")
    void resolvesStatutoryParameterByDate() {
        StatutoryParameter parameter = new StatutoryParameter();
        parameter.setCode("VALOR_UNIDAD_TRIBUTARIA_REFERENCIAL");
        parameter.setDescription("Valor de referencia, no validado");
        parameter.setValue(new BigDecimal("5240.5000"));
        parameter.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        statutoryParameterRepository.save(parameter);

        assertThat(lookupService.parameterAsOf("VALOR_UNIDAD_TRIBUTARIA_REFERENCIAL", LocalDate.of(2025, 6, 1)))
                .contains(new BigDecimal("5240.5000"));
        assertThat(lookupService.parameterAsOf("VALOR_UNIDAD_TRIBUTARIA_REFERENCIAL", LocalDate.of(2024, 1, 1)))
                .isEmpty();
    }

    private void saveBracket(String schemeCode, int order, String lower, String upper, String rate, LocalDate from) {
        TaxBracket bracket = new TaxBracket();
        bracket.setSchemeCode(schemeCode);
        bracket.setBracketOrder(order);
        bracket.setLowerBound(new BigDecimal(lower));
        bracket.setUpperBound(upper == null ? null : new BigDecimal(upper));
        bracket.setRate(new BigDecimal(rate));
        bracket.setEffectiveFrom(from);
        taxBracketRepository.save(bracket);
    }
}
