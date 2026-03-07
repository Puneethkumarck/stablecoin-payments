package com.stablecoin.payments.compliance.infrastructure.provider.worldcheck;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // given
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

            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            // when
            var result = adapter.screen(senderId, recipientId);

            // then
            assertThat(result.senderScreened()).isTrue();
            assertThat(result.recipientScreened()).isTrue();
            assertThat(result.senderHit()).isFalse();
            assertThat(result.recipientHit()).isFalse();
            assertThat(result.hitDetails()).isNull();
            assertThat(result.listsChecked()).containsExactly("OFAC_SDN", "EU_CONSOLIDATED", "UN");
            assertThat(result.provider()).isEqualTo("world-check");
            assertThat(result.providerRef()).startsWith("wc:");
            assertThat(result.screenedAt()).isNotNull();

            // Verify both sender and recipient were screened (2 API calls)
            wireMock.verify(2, postRequestedFor(urlEqualTo("/v2/cases/screeningRequest")));
        }
    }

    @Nested
    @DisplayName("Sanctions hit detected")
    class HitDetected {

        @Test
        @DisplayName("should detect sender hit and strip PII from details")
        void senderHitDetected() {
            // given
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

            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            // when
            var result = adapter.screen(senderId, recipientId);

            // then
            assertThat(result.senderHit()).isTrue();
            assertThat(result.recipientHit()).isTrue(); // both get same response
            assertThat(result.hitDetails()).isNotNull();
            // PII stripped: hitDetails should NOT contain actual names
            assertThat(result.hitDetails()).doesNotContain("John Doe");
            assertThat(result.hitDetails()).doesNotContain("John D.");
            // But should contain match metadata
            assertThat(result.hitDetails()).contains("STRONG");
            assertThat(result.hitDetails()).contains("OFAC_SDN");
        }

        @Test
        @DisplayName("should detect weak match as no hit")
        void weakMatchIsNotHit() {
            // given
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

            // when
            var result = adapter.screen(UUID.randomUUID(), UUID.randomUUID());

            // then
            assertThat(result.senderHit()).isFalse();
            assertThat(result.recipientHit()).isFalse();
            assertThat(result.hitDetails()).isNull();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when API returns server error")
        void serverError() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse().withStatus(500)));

            // when / then
            assertThatThrownBy(() -> adapter.screen(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when API returns 401 unauthorized")
        void unauthorizedError() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v2/cases/screeningRequest"))
                    .willReturn(aResponse().withStatus(401)));

            // when / then
            assertThatThrownBy(() -> adapter.screen(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }
    }
}
