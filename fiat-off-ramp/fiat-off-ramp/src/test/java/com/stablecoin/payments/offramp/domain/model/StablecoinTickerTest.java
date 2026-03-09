package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StablecoinTickerTest {

    @Test
    @DisplayName("USDC auto-fills issuer and decimals")
    void usdcAutoFills() {
        var actual = StablecoinTicker.of("USDC");

        var expected = new StablecoinTicker("USDC", "circle", 6);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("USDT auto-fills issuer and decimals")
    void usdtAutoFills() {
        var ticker = StablecoinTicker.of("USDT");

        assertThat(ticker.issuer()).isEqualTo("tether");
        assertThat(ticker.decimals()).isEqualTo(6);
    }

    @Test
    @DisplayName("EURC auto-fills issuer and decimals")
    void eurcAutoFills() {
        var ticker = StablecoinTicker.of("EURC");

        assertThat(ticker.issuer()).isEqualTo("circle_euro");
        assertThat(ticker.decimals()).isEqualTo(6);
    }

    @Test
    @DisplayName("PYUSD auto-fills issuer and decimals")
    void pyusdAutoFills() {
        var ticker = StablecoinTicker.of("PYUSD");

        assertThat(ticker.issuer()).isEqualTo("paypal");
        assertThat(ticker.decimals()).isEqualTo(6);
    }

    @Test
    @DisplayName("RLUSD auto-fills issuer and decimals")
    void rlusdAutoFills() {
        var ticker = StablecoinTicker.of("RLUSD");

        assertThat(ticker.issuer()).isEqualTo("ripple");
        assertThat(ticker.decimals()).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USDC", "USDT", "EURC", "PYUSD", "RLUSD"})
    @DisplayName("all supported tickers have 6 decimals")
    void allSupportedTickersHave6Decimals(String tickerName) {
        var ticker = StablecoinTicker.of(tickerName);

        assertThat(ticker.decimals()).isEqualTo(6);
    }

    @Test
    @DisplayName("throws for unsupported ticker")
    void throwsForUnsupportedTicker() {
        assertThatThrownBy(() -> StablecoinTicker.of("DOGE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported stablecoin")
                .hasMessageContaining("DOGE");
    }

    @Test
    @DisplayName("throws when ticker is null")
    void throwsWhenTickerNull() {
        assertThatThrownBy(() -> StablecoinTicker.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ticker");
    }

    @Test
    @DisplayName("throws when ticker is blank")
    void throwsWhenTickerBlank() {
        assertThatThrownBy(() -> StablecoinTicker.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("explicit constructor preserves provided issuer")
    void explicitConstructorPreservesIssuer() {
        var ticker = new StablecoinTicker("USDC", "custom_issuer", 6);

        assertThat(ticker.issuer()).isEqualTo("custom_issuer");
    }

    @Test
    @DisplayName("explicit constructor fills issuer when null")
    void explicitConstructorFillsIssuerWhenNull() {
        var ticker = new StablecoinTicker("USDC", null, 6);

        assertThat(ticker.issuer()).isEqualTo("circle");
    }

    @Test
    @DisplayName("explicit constructor fills decimals when zero")
    void explicitConstructorFillsDecimalsWhenZero() {
        var ticker = new StablecoinTicker("USDC", "circle", 0);

        assertThat(ticker.decimals()).isEqualTo(6);
    }
}
