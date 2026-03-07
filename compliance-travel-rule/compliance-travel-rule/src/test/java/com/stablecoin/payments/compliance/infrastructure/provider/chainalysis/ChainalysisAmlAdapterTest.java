package com.stablecoin.payments.compliance.infrastructure.provider.chainalysis;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.compliance.domain.model.AmlResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChainalysisAmlAdapterTest {

    private static WireMockServer wireMock;
    private ChainalysisAmlAdapter adapter;

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
        var properties = new ChainalysisProperties(
                wireMock.baseUrl() + "/v2",
                "test-api-key",
                2
        );
        adapter = new ChainalysisAmlAdapter(properties);
    }

    private void stubRegisterTransfer() {
        wireMock.stubFor(post(urlPathMatching("/v2/users/.+/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"externalId\":\"transfer-registered\"}")));
    }

    private void stubGetTransferClean() {
        wireMock.stubFor(get(urlPathMatching("/v2/users/.+/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "updatedAt": "2026-03-08T00:00:00Z",
                              "asset": "USDC",
                              "cluster": "known-exchange",
                              "rating": "lowRisk",
                              "alerts": []
                            }
                            """)));
    }

    private void stubGetTransferFlagged() {
        wireMock.stubFor(get(urlPathMatching("/v2/users/.+/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "updatedAt": "2026-03-08T00:00:00Z",
                              "asset": "USDC",
                              "cluster": "darknet-market",
                              "rating": "highRisk",
                              "alerts": [
                                {
                                  "alertLevel": "SEVERE",
                                  "category": "darknet market",
                                  "service": "KYT",
                                  "externalId": 12345
                                },
                                {
                                  "alertLevel": "HIGH",
                                  "category": "sanctions",
                                  "service": "KYT",
                                  "externalId": 12346
                                }
                              ]
                            }
                            """)));
    }

    @Nested
    @DisplayName("Clean analysis — no flags")
    class CleanAnalysis {

        @Test
        @DisplayName("should return clean result when no alerts")
        void cleanResult() {
            stubRegisterTransfer();
            stubGetTransferClean();

            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var result = adapter.analyze(senderId, recipientId);

            var expected = AmlResult.builder()
                    .flagged(false)
                    .flagReasons(List.of())
                    .provider("chainalysis")
                    .providerRef("chainalysis:%s/%s".formatted(senderId, recipientId))
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("amlResultId", "checkId", "screenedAt", "chainAnalysis")
                    .isEqualTo(expected);
            assertThat(result.chainAnalysis()).contains("lowRisk").contains("known-exchange");
            assertThat(result.screenedAt()).isNotNull();

            wireMock.verify(2, postRequestedFor(urlPathMatching("/v2/users/.+/transfers")));
            wireMock.verify(2, getRequestedFor(urlPathMatching("/v2/users/.+/transfers")));
        }
    }

    @Nested
    @DisplayName("Flagged analysis")
    class FlaggedAnalysis {

        @Test
        @DisplayName("should flag when HIGH or SEVERE alerts present")
        void flaggedResult() {
            stubRegisterTransfer();
            stubGetTransferFlagged();

            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var result = adapter.analyze(senderId, recipientId);

            assertThat(result.flagged()).isTrue();
            assertThat(result.flagReasons()).hasSize(4);
            assertThat(result.flagReasons())
                    .anyMatch(r -> r.contains("SEVERE") && r.contains("darknet market"))
                    .anyMatch(r -> r.contains("HIGH") && r.contains("sanctions"));
            assertThat(result.provider()).isEqualTo("chainalysis");
            assertThat(result.chainAnalysis()).contains("highRisk").contains("darknet-market");
        }

        @Test
        @DisplayName("should ignore LOW alert levels")
        void lowAlertsIgnored() {
            stubRegisterTransfer();
            wireMock.stubFor(get(urlPathMatching("/v2/users/.+/transfers"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "updatedAt": "2026-03-08T00:00:00Z",
                                  "asset": "USDC",
                                  "cluster": "known-exchange",
                                  "rating": "lowRisk",
                                  "alerts": [
                                    {
                                      "alertLevel": "LOW",
                                      "category": "mining",
                                      "service": "KYT",
                                      "externalId": 99999
                                    }
                                  ]
                                }
                                """)));

            var result = adapter.analyze(UUID.randomUUID(), UUID.randomUUID());

            var expected = AmlResult.builder()
                    .flagged(false)
                    .flagReasons(List.of())
                    .provider("chainalysis")
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("amlResultId", "checkId", "screenedAt", "chainAnalysis", "providerRef")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when register transfer returns server error")
        void registerTransferServerError() {
            wireMock.stubFor(post(urlPathMatching("/v2/users/.+/transfers"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> adapter.analyze(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when get analysis returns 401")
        void getAnalysisUnauthorized() {
            stubRegisterTransfer();
            wireMock.stubFor(get(urlPathMatching("/v2/users/.+/transfers"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> adapter.analyze(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }
    }
}
