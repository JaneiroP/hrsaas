package com.ohmdevs.hrsaas.shared.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comportamiento determinista de {@link Money}; los invariantes con datos aleatorios están en {@link MoneyPropertyTest}. */
class MoneyTest {

    @Test
    @DisplayName("el constructor rechaza un amount que no viene ya a escala 2")
    void constructorRejectsWrongScale() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.5"), "DOP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("10.500"), "DOP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of() redondea HALF_UP por defecto en el borde del concepto")
    void ofRoundsHalfUpByDefault() {
        assertThat(Money.of(new BigDecimal("10.125"), "DOP").amount()).isEqualByComparingTo("10.13");
        assertThat(Money.of(new BigDecimal("10.124"), "DOP").amount()).isEqualByComparingTo("10.12");
    }

    @Test
    @DisplayName("of() acepta una estrategia de redondeo explícita")
    void ofAcceptsExplicitRoundingMode() {
        assertThat(Money.of(new BigDecimal("10.125"), "DOP", RoundingMode.DOWN).amount())
                .isEqualByComparingTo("10.12");
    }

    @Test
    @DisplayName("sumar o restar monedas distintas lanza IllegalArgumentException")
    void mixingCurrenciesIsRejected() {
        Money dop = Money.of(BigDecimal.TEN, "DOP");
        Money usd = Money.of(BigDecimal.TEN, "USD");

        assertThatThrownBy(() -> dop.add(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dop.subtract(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dop.compareTo(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("zero, isZero, isPositive, isNegative y negate se comportan como se espera")
    void signHelpers() {
        Money zero = Money.zero("DOP");
        Money positive = Money.of(new BigDecimal("5.00"), "DOP");
        Money negative = positive.negate();

        assertThat(zero.isZero()).isTrue();
        assertThat(positive.isPositive()).isTrue();
        assertThat(negative.isNegative()).isTrue();
        assertThat(negative.amount()).isEqualByComparingTo("-5.00");
    }
}
