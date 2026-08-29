package com.ohmdevs.hrsaas.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Importe con moneda (AD-05). {@code BigDecimal} siempre, {@code double}
 * jamás — persistido a escala {@value #SCALE} ({@code NUMERIC(15,2)}).
 *
 * <p>El constructor exige que {@code amount} ya venga a escala
 * {@value #SCALE}: un {@code Money} nunca queda en un estado a medio
 * redondear. Un cálculo intermedio (aplicar una tasa, prorratear días) se
 * hace en {@code BigDecimal} suelto a escala {@link #INTERMEDIATE_SCALE}
 * — no en {@code Money} — y solo se convierte a {@code Money} en el borde
 * del concepto, vía {@link #of(BigDecimal, String)}: ese es el único punto
 * de redondeo, no uno por cada paso de la fórmula. Sumar/restar dos
 * {@code Money} ya redondeados nunca reintroduce error de redondeo porque
 * {@code BigDecimal.add}/{@code subtract} no pierden escala.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    /** Escala de persistencia — coincide con {@code NUMERIC(15,2)} en toda tabla de dinero. */
    public static final int SCALE = 2;

    /** Escala de los cálculos intermedios, antes de redondear al borde del concepto. */
    public static final int INTERMEDIATE_SCALE = 6;

    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("La moneda debe ser un código ISO 4217 de 3 letras: '" + currency + "'");
        }
        if (amount.scale() != SCALE) {
            throw new IllegalArgumentException(
                    "Money debe construirse ya a escala %d (tenía %d); use Money.of(...) para redondear desde un intermedio"
                            .formatted(SCALE, amount.scale()));
        }
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO.setScale(SCALE), currency);
    }

    /**
     * Redondea un {@code BigDecimal} intermedio (normalmente a escala
     * {@value #INTERMEDIATE_SCALE}) al borde del concepto. Único punto de
     * redondeo del motor — nunca redondear a mano en medio de una fórmula.
     */
    public static Money of(BigDecimal rawAmount, String currency) {
        return of(rawAmount, currency, DEFAULT_ROUNDING);
    }

    /**
     * Con estrategia de redondeo explícita — la política es configurable
     * por tenant (blueprint §04); esta clase no decide cuál, solo aplica la
     * que le pasan. {@link #of(BigDecimal, String)} usa
     * {@code HALF_UP} como valor por defecto para el resto del código que
     * todavía no lee la configuración del tenant.
     */
    public static Money of(BigDecimal rawAmount, String currency, RoundingMode roundingMode) {
        return new Money(rawAmount.setScale(SCALE, roundingMode), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "No se pueden combinar montos en monedas distintas: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }
}
