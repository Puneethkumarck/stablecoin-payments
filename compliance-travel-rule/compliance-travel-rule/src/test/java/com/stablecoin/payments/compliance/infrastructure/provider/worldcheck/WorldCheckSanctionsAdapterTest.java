package com.stablecoin.payments.compliance.infrastructure.provider.worldcheck;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldCheckSanctionsAdapterTest {

    private static WireMockServer wireMock;
    private WorldCheckSanctionsAdapter adapter;

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
        var properties = new WorldCheckProperties(
                wireMock.baseUrl() + "/v2",
                "test-api-key",
                "test-api-secret",
                "test-group",
                2
        );
        adapter = new WorldCheckSanctionsAdapter(properties);
    }

    @Nested
    @DisplayName("No sanctions hit")
    class NoHit {

        @Test
        @DisplayName("should screen both sender and recipient with no hits")
        void noHitsReturned() {
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "caseId": "test-id",
                                  "caseSystemId": "WC-001",
                                  "status": "COMPLETED",
                                  "results": []
                                }
                                """)));

            var result = adapter.screen(UUID.randomUUID(), UUID.randomUUID());

            var expected = SanctionsResult.builder()
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(false)
                    .hitDetails(null)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("world-check")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("sanctionsResultId", "checkId", "providerRef", "screenedAt")
                    .isEqualTo(expected);
            assertThat(result.providerRef()).startsWith("wc:");
            assertThat(result.screenedAt()).isNotNull();

            wireMock.verify(2, postRequestedFor(urlEqualTo("/v2/cases/screeningRequest")));
        }
    }

    @Nested
    @DisplayName("Sanctions hit detected")
    class HitDetected {

        @Test
        @DisplayName("should detect hit and strip PII from details")
        void hitDetectedWithPiiStripped() {
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "caseId": "test-id",
                                  "caseSystemId": "WC-002",
                                  "status": "COMPLETED",
                                  "results": [
                                    {
                                      "referenceId": "REF-001",
                                      "matchStrength": "STRONG",
                                      "matchedTerm": "John Doe",
                                      "matchedNameType": "PRIMARY",
                                      "submittedTerm": "John D.",
                                      "matchedLists": ["OFAC_SDN"],
                                      "categories": ["SANCTIONS"]
                                    }
                                  ]
                                }
                                """)));

            var result = adapter.screen(UUID.randomUUID(), UUID.randomUUID());

            var expected = SanctionsResult.builder()
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(true)
                    .recipientHit(true)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("world-check")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("sanctionsResultId", "checkId", "providerRef", "screenedAt", "hitDetails")
                    .isEqualTo(expected);

            // PII stripped: no personal names, only match metadata
            assertThat(result.hitDetails())
                    .doesNotContain("John Doe")
                    .doesNotContain("John D.")
                    .contains("STRONG")
                    .contains("OFAC_SDN");
        }

        @Test
        @DisplayName("should detect weak match as no hit")
        void weakMatchIsNotHit() {
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "caseId": "test-id",
                                  "caseSystemId": "WC-003",
                                  "status": "COMPLETED",
                                  "results": [
                                    {
                                      "referenceId": "REF-002",
                                      "matchStrength": "WEAK",
                                      "matchedTerm": "Similar Name",
                                      "matchedNameType": "ALIAS",
                                      "submittedTerm": "Some Name",
                                      "matchedLists": ["UN"],
                                      "categories": ["PEP"]
                                    }
                                  ]
                                }
                                """)));

            var result = adapter.screen(UUID.randomUUID(), UUID.randomUUID());

            var expected = SanctionsResult.builder()
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(false)
                    .hitDetails(null)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("world-check")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("sanctionsResultId", "checkId", "providerRef", "screenedAt")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when API returns server error")
        void serverError() {
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> adapter.screen(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when API returns 401 unauthorized")
        void unauthorizedError() {
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> adapter.screen(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }
    }
}
