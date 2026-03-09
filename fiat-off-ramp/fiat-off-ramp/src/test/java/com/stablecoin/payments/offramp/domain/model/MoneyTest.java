package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("creates valid money")
    void createsValidMoney() {
        var money = new Money(new BigDecimal("1000.00"), "USD");

        var expected = new Money(new BigDecimal("1000.00"), "USD");
        assertThat(money)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("throws when amount is null")
    void throwsWhenAmountNull() {
        assertThatThrownBy(() -> new Money(null, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount");
    }

    @Test
    @DisplayName("throws when amount is zero")
    void throwsWhenAmountZero() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("throws when amount is negative")
    void throwsWhenAmountNegative() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-50"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("throws when currency is null")
    void throwsWhenCurrencyNull() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    @DisplayName("throws when currency is blank")
    void throwsWhenCurrencyBlank() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100"), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}
