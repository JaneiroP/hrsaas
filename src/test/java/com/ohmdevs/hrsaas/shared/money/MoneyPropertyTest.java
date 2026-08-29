package com.ohmdevs.hrsaas.shared.money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariantes de dinero con datos aleatorios (AD-05, blueprint §11
 * "Propiedades"). Verificado en vivo que jqwik 1.9.3 corre correctamente
 * contra el JUnit Platform 6.0.3 de este proyecto, aunque su propio pom
 * declara la línea 1.13.x (la de JUnit 5) — 1000 casos + 25 edge cases
 * automáticos, verde.
 */
class MoneyPropertyTest {

    @Property
    void addingTwoRoundedAmountsNeverLosesACent(
            @ForAll @BigRange(min = "0.00", max = "999999.99") BigDecimal a,
            @ForAll @BigRange(min = "0.00", max = "999999.99") BigDecimal b) {
        Money first = Money.of(a.setScale(Money.INTERMEDIATE_SCALE), "DOP");
        Money second = Money.of(b.setScale(Money.INTERMEDIATE_SCALE), "DOP");

        Money sum = first.add(second);

        BigDecimal expected = first.amount().add(second.amount());
        assertThat(sum.amount()).isEqualByComparingTo(expected);
        assertThat(sum.amount().scale()).isEqualTo(Money.SCALE);
    }

    /**
     * El invariante central del motor de nómina (blueprint §11): la suma de
     * las líneas de un volante debe cuadrar exacto con el total, sin
     * importar cuántas líneas ni sus montos. Sumar {@link Money} ya
     * redondeados nunca reintroduce error porque {@code BigDecimal.add} no
     * pierde escala — este test lo verifica con listas aleatorias en vez de
     * darlo por sentado.
     */
    @Property
    void sumOfLineItemsAlwaysEqualsIndependentlyComputedTotal(@ForAll("moneyLists") List<Money> lines) {
        Money sum = lines.stream().reduce(Money.zero("DOP"), Money::add);

        BigDecimal independentTotal = lines.stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO.setScale(Money.SCALE), BigDecimal::add);

        assertThat(sum.amount()).isEqualByComparingTo(independentTotal);
    }

    @Provide
    Arbitrary<List<Money>> moneyLists() {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("-9999.99"), new BigDecimal("9999.99"))
                .ofScale(Money.SCALE);
        return amounts.map(amount -> Money.of(amount, "DOP")).list().ofMinSize(1).ofMaxSize(50);
    }
}
