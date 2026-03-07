package com.stablecoin.payments.compliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money value object")
class MoneyTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create Money with valid amount and currency")
        void should_createMoney_when_validInputs() {
            var money = new Money(new BigDecimal("100.50"), "USD");

            var expected = new Money(new BigDecimal("100.50"), "USD");
            assertThat(money)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should create Money with zero amount")
        void should_createMoney_when_amountIsZero() {
            var money = new Money(BigDecimal.ZERO, "EUR");

            var expected = new Money(BigDecimal.ZERO, "EUR");
            assertThat(money)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when amount is negative")
        void should_throw_when_amountIsNegative() {
            assertThatThrownBy(() -> new Money(new BigDecimal("-1"), "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("should throw when amount is null")
        void should_throw_when_amountIsNull() {
            assertThatThrownBy(() -> new Money(null, "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("should throw when currency is null")
        void should_throw_when_currencyIsNull() {
            assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should throw when currency is blank")
        void should_throw_when_currencyIsBlank() {
            assertThatThrownBy(() -> new Money(BigDecimal.TEN, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should throw when currency is empty")
        void should_throw_when_currencyIsEmpty() {
            assertThatThrownBy(() -> new Money(BigDecimal.TEN, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
    }

    @Nested
    @DisplayName("isAboveThreshold()")
    class IsAboveThreshold {

        @Test
        @DisplayName("should return true when amount is above threshold")
        void should_returnTrue_when_amountAboveThreshold() {
            var money = new Money(new BigDecimal("1001"), "USD");

            assertThat(money.isAboveThreshold(new BigDecimal("1000"))).isTrue();
        }

        @Test
        @DisplayName("should return true when amount equals threshold")
        void should_returnTrue_when_amountEqualsThreshold() {
            var money = new Money(new BigDecimal("1000"), "USD");

            assertThat(money.isAboveThreshold(new BigDecimal("1000"))).isTrue();
        }

        @Test
        @DisplayName("should return false when amount is below threshold")
        void should_returnFalse_when_amountBelowThreshold() {
            var money = new Money(new BigDecimal("999.99"), "USD");

            assertThat(money.isAboveThreshold(new BigDecimal("1000"))).isFalse();
        }

        @Test
        @DisplayName("should return true when amount is zero and threshold is zero")
        void should_returnTrue_when_amountAndThresholdAreZero() {
            var money = new Money(BigDecimal.ZERO, "USD");

            assertThat(money.isAboveThreshold(BigDecimal.ZERO)).isTrue();
        }
    }
}
