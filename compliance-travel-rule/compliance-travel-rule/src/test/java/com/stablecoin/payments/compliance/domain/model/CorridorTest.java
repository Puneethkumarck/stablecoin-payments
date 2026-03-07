package com.stablecoin.payments.compliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Corridor value object")
class CorridorTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create Corridor with valid fields")
        void should_createCorridor_when_validInputs() {
            var corridor = new Corridor("US", "DE", "USD", "EUR");

            var expected = new Corridor("US", "DE", "USD", "EUR");
            assertThat(corridor)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when sourceCountry is null")
        void should_throw_when_sourceCountryIsNull() {
            assertThatThrownBy(() -> new Corridor(null, "DE", "USD", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCountry");
        }

        @Test
        @DisplayName("should throw when sourceCountry is blank")
        void should_throw_when_sourceCountryIsBlank() {
            assertThatThrownBy(() -> new Corridor("  ", "DE", "USD", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCountry");
        }

        @Test
        @DisplayName("should throw when targetCountry is null")
        void should_throw_when_targetCountryIsNull() {
            assertThatThrownBy(() -> new Corridor("US", null, "USD", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCountry");
        }

        @Test
        @DisplayName("should throw when targetCountry is blank")
        void should_throw_when_targetCountryIsBlank() {
            assertThatThrownBy(() -> new Corridor("US", "", "USD", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCountry");
        }

        @Test
        @DisplayName("should throw when sourceCurrency is null")
        void should_throw_when_sourceCurrencyIsNull() {
            assertThatThrownBy(() -> new Corridor("US", "DE", null, "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCurrency");
        }

        @Test
        @DisplayName("should throw when sourceCurrency is blank")
        void should_throw_when_sourceCurrencyIsBlank() {
            assertThatThrownBy(() -> new Corridor("US", "DE", " ", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCurrency");
        }

        @Test
        @DisplayName("should throw when targetCurrency is null")
        void should_throw_when_targetCurrencyIsNull() {
            assertThatThrownBy(() -> new Corridor("US", "DE", "USD", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCurrency");
        }

        @Test
        @DisplayName("should throw when targetCurrency is blank")
        void should_throw_when_targetCurrencyIsBlank() {
            assertThatThrownBy(() -> new Corridor("US", "DE", "USD", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCurrency");
        }
    }

    @Nested
    @DisplayName("isCrossBorder()")
    class IsCrossBorder {

        @Test
        @DisplayName("should return true when source and target countries differ")
        void should_returnTrue_when_countriesDiffer() {
            var corridor = new Corridor("US", "DE", "USD", "EUR");

            assertThat(corridor.isCrossBorder()).isTrue();
        }

        @Test
        @DisplayName("should return false when source and target countries are the same")
        void should_returnFalse_when_countriesAreSame() {
            var corridor = new Corridor("US", "US", "USD", "USD");

            assertThat(corridor.isCrossBorder()).isFalse();
        }
    }
}
