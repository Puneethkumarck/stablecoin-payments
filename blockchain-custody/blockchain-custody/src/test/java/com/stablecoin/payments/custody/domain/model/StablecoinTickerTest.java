package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StablecoinTicker")
class StablecoinTickerTest {

    @Nested
    @DisplayName("Valid Tickers via of()")
    class ValidTickers {

        @Test
        @DisplayName("USDC maps to circle issuer with 6 decimals")
        void usdcMapsCorrectly() {
            var ticker = StablecoinTicker.of("USDC");

            assertThat(ticker.ticker()).isEqualTo("USDC");
            assertThat(ticker.issuer()).isEqualTo("circle");
            assertThat(ticker.decimals()).isEqualTo(6);
        }

        @Test
        @DisplayName("USDT maps to tether issuer with 6 decimals")
        void usdtMapsCorrectly() {
            var ticker = StablecoinTicker.of("USDT");

            assertThat(ticker.ticker()).isEqualTo("USDT");
            assertThat(ticker.issuer()).isEqualTo("tether");
            assertThat(ticker.decimals()).isEqualTo(6);
        }

        @Test
        @DisplayName("EURC maps to circle_euro issuer with 6 decimals")
        void eurcMapsCorrectly() {
            var ticker = StablecoinTicker.of("EURC");

            assertThat(ticker.ticker()).isEqualTo("EURC");
            assertThat(ticker.issuer()).isEqualTo("circle_euro");
            assertThat(ticker.decimals()).isEqualTo(6);
        }

        @Test
        @DisplayName("PYUSD maps to paypal issuer with 6 decimals")
        void pyusdMapsCorrectly() {
            var ticker = StablecoinTicker.of("PYUSD");

            assertThat(ticker.ticker()).isEqualTo("PYUSD");
            assertThat(ticker.issuer()).isEqualTo("paypal");
            assertThat(ticker.decimals()).isEqualTo(6);
        }

        @Test
        @DisplayName("RLUSD maps to ripple issuer with 6 decimals")
        void rlusdMapsCorrectly() {
            var ticker = StablecoinTicker.of("RLUSD");

            assertThat(ticker.ticker()).isEqualTo("RLUSD");
            assertThat(ticker.issuer()).isEqualTo("ripple");
            assertThat(ticker.decimals()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Constructor with explicit issuer and decimals")
    class ExplicitConstructor {

        @Test
        @DisplayName("uses provided issuer when non-null and non-blank")
        void usesProvidedIssuer() {
            var ticker = new StablecoinTicker("USDC", "custom-issuer", 6);

            assertThat(ticker.issuer()).isEqualTo("custom-issuer");
        }

        @Test
        @DisplayName("uses provided decimals when positive")
        void usesProvidedDecimals() {
            var ticker = new StablecoinTicker("USDC", "circle", 18);

            assertThat(ticker.decimals()).isEqualTo(18);
        }

        @Test
        @DisplayName("falls back to default issuer when null provided")
        void fallsBackWhenIssuerNull() {
            var ticker = new StablecoinTicker("USDC", null, 6);

            assertThat(ticker.issuer()).isEqualTo("circle");
        }

        @Test
        @DisplayName("falls back to default issuer when blank provided")
        void fallsBackWhenIssuerBlank() {
            var ticker = new StablecoinTicker("USDC", "  ", 6);

            assertThat(ticker.issuer()).isEqualTo("circle");
        }

        @Test
        @DisplayName("falls back to default decimals when zero provided")
        void fallsBackWhenDecimalsZero() {
            var ticker = new StablecoinTicker("USDC", "circle", 0);

            assertThat(ticker.decimals()).isEqualTo(6);
        }

        @Test
        @DisplayName("falls back to default decimals when negative provided")
        void fallsBackWhenDecimalsNegative() {
            var ticker = new StablecoinTicker("USDC", "circle", -1);

            assertThat(ticker.decimals()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Invalid Values")
    class InvalidValues {

        @Test
        @DisplayName("rejects null ticker")
        void rejectsNullTicker() {
            assertThatThrownBy(() -> StablecoinTicker.of(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ticker is required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "\t"})
        @DisplayName("rejects blank ticker")
        void rejectsBlankTicker(String value) {
            assertThatThrownBy(() -> StablecoinTicker.of(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ticker is required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"DAI", "BUSD", "UST", "usdc", "Usdc"})
        @DisplayName("rejects unsupported tickers")
        void rejectsUnsupported(String value) {
            assertThatThrownBy(() -> StablecoinTicker.of(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported stablecoin");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("two tickers with same values are equal")
        void equalWhenSameValues() {
            var a = StablecoinTicker.of("USDC");
            var b = StablecoinTicker.of("USDC");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two tickers with different values are not equal")
        void notEqualWhenDifferent() {
            var a = StablecoinTicker.of("USDC");
            var b = StablecoinTicker.of("USDT");

            assertThat(a).isNotEqualTo(b);
        }
    }
}
