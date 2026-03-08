package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("creates Money with positive amount and currency")
        void createsMoneyWithPositiveAmountAndCurrency() {
            var money = new Money(new BigDecimal("100.50"), "USD");

            var expected = new Money(new BigDecimal("100.50"), "USD");

            assertThat(money)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("creates Money with very small positive amount")
        void createsMoneyWithSmallPositiveAmount() {
            var money = new Money(new BigDecimal("0.01"), "EUR");

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
            assertThat(money.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("creates Money with large amount")
        void createsMoneyWithLargeAmount() {
            var money = new Money(new BigDecimal("999999999.99"), "GBP");

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
        }
    }

    @Nested
    @DisplayName("Invalid Construction")
    class InvalidConstruction {

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
            assertThatThrownBy(() -> new Money(new BigDecimal("100.00"), "   "))
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
    }
}
