package com.stablecoin.payments.fx.infrastructure.provider.refinitiv;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.fx.domain.model.CorridorRate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefinitivRateAdapterTest {

    private static WireMockServer wireMock;
    private RefinitivRateAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        var properties = new RefinitivProperties(
                wireMock.baseUrl(), "test-api-key", 2);
        adapter = new RefinitivRateAdapter(properties);
    }

    @Nested
    @DisplayName("Successful rate fetch")
    class SuccessfulFetch {

        @Test
        @DisplayName("should return rate for valid currency pair")
        void returnsRate() {
            long nowMs = System.currentTimeMillis();
            wireMock.stubFor(get(urlEqualTo("/data/pricing/v1/rates/USDEUR"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "currencyPair": "USDEUR",
                                  "mid": 0.9200000000,
                                  "bid": 0.9195000000,
                                  "ask": 0.9205000000,
                                  "timestamp": %d
                                }
                                """.formatted(nowMs))));

            var result = adapter.getRate("USD", "EUR");

            assertThat(result).isPresent();
            var expected = CorridorRate.builder()
                    .fromCurrency("USD")
                    .toCurrency("EUR")
                    .rate(new BigDecimal("0.92"))
                    .spreadBps(30)
                    .feeBps(30)
                    .provider("refinitiv")
                    .ageMs(0)
                    .build();
            assertThat(result.get())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .ignoringFields("ageMs")
                    .isEqualTo(expected);
            assertThat(result.get().ageMs()).isBetween(0, 5000);

            wireMock.verify(1, getRequestedFor(urlEqualTo("/data/pricing/v1/rates/USDEUR")));
        }

        @Test
        @DisplayName("should return empty when null body returned")
        void nullBody() {
            wireMock.stubFor(get(urlEqualTo("/data/pricing/v1/rates/USDGBP"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            var result = adapter.getRate("USD", "GBP");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Provider name")
    class ProviderName {

        @Test
        @DisplayName("should return refinitiv as provider name")
        void returnsProviderName() {
            assertThat(adapter.providerName()).isEqualTo("refinitiv");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw on server error")
        void serverError() {
            wireMock.stubFor(get(urlEqualTo("/data/pricing/v1/rates/USDEUR"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> adapter.getRate("USD", "EUR"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw on 401 unauthorized")
        void unauthorizedError() {
            wireMock.stubFor(get(urlEqualTo("/data/pricing/v1/rates/USDEUR"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> adapter.getRate("USD", "EUR"))
                    .isInstanceOf(Exception.class);
        }
    }
}
