package com.stablecoin.payments.offramp.infrastructure.provider.circle;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.offramp.domain.port.RedemptionRequest;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircleRedemptionAdapterTest {

    private static WireMockServer wireMock;
    private CircleRedemptionAdapter adapter;

    private static final UUID PAYOUT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String DESTINATION_ID = "wire-bank-001";

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
        var properties = new CircleProperties(wireMock.baseUrl(), "SAND_API_KEY_TEST", DESTINATION_ID, 10);
        adapter = new CircleRedemptionAdapter(properties);
    }

    private RedemptionRequest aRedemptionRequest() {
        return new RedemptionRequest(PAYOUT_ID, "USDC", new BigDecimal("10000.000000"));
    }

    @Nested
    @DisplayName("redeem")
    class Redeem {

        @Test
        @DisplayName("should return RedemptionResult on successful Circle payout")
        void redeem_success() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "id": "circle-payout-ref-001",
                                        "amount": {
                                          "amount": "10000.00",
                                          "currency": "USD"
                                        },
                                        "status": "pending",
                                        "createDate": "2026-03-10T12:00:00.000Z"
                                      }
                                    }
                                    """)));

            var result = adapter.redeem(aRedemptionRequest());

            var expected = new RedemptionResult(
                    "circle-payout-ref-001",
                    new BigDecimal("10000.00"),
                    "USD",
                    result.redeemedAt()
            );
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("redeemedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when Circle returns 400 bad request")
        void redeem_badRequest() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "code": 2,
                                      "message": "Invalid idempotency key"
                                    }
                                    """)));

            assertThatThrownBy(() -> adapter.redeem(aRedemptionRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when Circle returns 401 unauthorized")
        void redeem_unauthorized() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "code": 401,
                                      "message": "Malformed authorization."
                                    }
                                    """)));

            assertThatThrownBy(() -> adapter.redeem(aRedemptionRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw on connection timeout")
        void redeem_timeout() {
            var timeoutProperties = new CircleProperties(wireMock.baseUrl(), "SAND_API_KEY_TEST", DESTINATION_ID, 1);
            var timeoutAdapter = new CircleRedemptionAdapter(timeoutProperties);

            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withFixedDelay(3000)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "id": "circle-payout-late",
                                        "amount": {
                                          "amount": "10000.00",
                                          "currency": "USD"
                                        },
                                        "status": "pending",
                                        "createDate": "2026-03-10T12:00:00.000Z"
                                      }
                                    }
                                    """)));

            assertThatThrownBy(() -> timeoutAdapter.redeem(aRedemptionRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should send correct JSON body and auth header to Circle")
        void redeem_verifiesRequestBodyAndAuth() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "id": "circle-payout-verify",
                                        "amount": {
                                          "amount": "10000.000000",
                                          "currency": "USD"
                                        },
                                        "status": "pending",
                                        "createDate": "2026-03-10T12:00:00.000Z"
                                      }
                                    }
                                    """)));

            adapter.redeem(aRedemptionRequest());

            wireMock.verify(postRequestedFor(urlEqualTo("/v1/businessAccount/payouts"))
                    .withHeader("Authorization", equalTo("Bearer SAND_API_KEY_TEST"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""
                            {
                              "idempotencyKey": "%s",
                              "destination": {
                                "type": "wire",
                                "id": "%s"
                              },
                              "amount": {
                                "amount": "10000.000000",
                                "currency": "USD"
                              }
                            }
                            """.formatted(PAYOUT_ID, DESTINATION_ID))));
        }

        @Test
        @DisplayName("should throw when Circle returns 500 server error")
        void redeem_serverError() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "code": 500,
                                      "message": "Internal server error"
                                    }
                                    """)));

            assertThatThrownBy(() -> adapter.redeem(aRedemptionRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle EUR currency in Circle response")
        void redeem_eurCurrency() {
            wireMock.stubFor(post(urlEqualTo("/v1/businessAccount/payouts"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "id": "circle-payout-eur-001",
                                        "amount": {
                                          "amount": "9200.00",
                                          "currency": "EUR"
                                        },
                                        "status": "pending",
                                        "createDate": "2026-03-10T12:00:00.000Z"
                                      }
                                    }
                                    """)));

            var result = adapter.redeem(aRedemptionRequest());

            var expected = new RedemptionResult(
                    "circle-payout-eur-001",
                    new BigDecimal("9200.00"),
                    "EUR",
                    result.redeemedAt()
            );
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("redeemedAt")
                    .isEqualTo(expected);
        }
    }
}
