package com.stablecoin.payments.orchestrator.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money value object")
class MoneyTest {

    @Test
    @DisplayName("creates valid Money with positive amount and currency")
    void createsValidMoney() {
        var money = new Money(new BigDecimal("100.50"), "USD");

        var expected = new Money(new BigDecimal("100.50"), "USD");

        assertThat(money)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("rejects null amount")
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    @Test
    @DisplayName("rejects zero amount")
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    @Test
    @DisplayName("rejects negative amount")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-10.00"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    @Test
    @DisplayName("rejects null currency")
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    @DisplayName("rejects blank currency")
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.00"), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    @DisplayName("rejects empty currency")
    void rejectsEmptyCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.00"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    @DisplayName("accepts small positive amount")
    void acceptsSmallPositiveAmount() {
        var money = new Money(new BigDecimal("0.01"), "EUR");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("accepts large amount")
    void acceptsLargeAmount() {
        var money = new Money(new BigDecimal("999999999.99"), "GBP");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
    }
}
