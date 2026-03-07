package com.stablecoin.payments.compliance.infrastructure.provider.onfido;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class OnfidoKycAdapterTest {

    private static WireMockServer wireMock;
    private OnfidoKycAdapter adapter;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        var properties = new OnfidoProperties(
                wireMock.baseUrl() + "/v3.6",
                "test-token",
                2,
                60
        );
        adapter = new OnfidoKycAdapter(properties, redisTemplate);
    }

    @Nested
    @DisplayName("Cache miss — API call")
    class CacheMiss {

        @Test
        @DisplayName("should verify both parties and return VERIFIED when checks are clear")
        void verifiedWhenClear() {
            given(valueOps.get(anyString())).willReturn(null);

            wireMock.stubFor(get(urlPathEqualTo("/v3.6/checks"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "checks": [
                                    {
                                      "id": "chk-001",
                                      "status": "complete",
                                      "result": "clear",
                                      "applicantId": "app-001",
                                      "reportIds": ["rpt-001"]
                                    }
                                  ]
                                }
                                """)));

            var result = adapter.verify(UUID.randomUUID(), UUID.randomUUID());

            var expected = KycResult.builder()
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("onfido")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("kycResultId", "checkId", "providerRef", "checkedAt")
                    .isEqualTo(expected);
            assertThat(result.providerRef()).startsWith("onfido:");

            // Verify caching happened for both sender and recipient (VERIFIED results are cached)
            then(valueOps).should(times(2)).set(anyString(), anyString(), eq(Duration.ofMinutes(60)));
        }

        @Test
        @DisplayName("should return PENDING when check result is consider")
        void pendingWhenConsider() {
            given(valueOps.get(anyString())).willReturn(null);

            wireMock.stubFor(get(urlPathEqualTo("/v3.6/checks"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                  "checks": [
                                    {
                                      "id": "chk-002",
                                      "status": "complete",
                                      "result": "consider",
                                      "applicantId": "app-002",
                                      "reportIds": ["rpt-002"]
                                    }
                                  ]
                                }
                                """)));

            var result = adapter.verify(UUID.randomUUID(), UUID.randomUUID());

            var expected = KycResult.builder()
                    .senderKycTier(KycTier.KYC_TIER_1)
                    .senderStatus(KycStatus.PENDING)
                    .recipientStatus(KycStatus.PENDING)
                    .provider("onfido")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("kycResultId", "checkId", "providerRef", "checkedAt")
                    .isEqualTo(expected);

            // PENDING should NOT be cached
            then(valueOps).should(never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Cache hit")
    class CacheHit {

        @Test
        @DisplayName("should return cached result without calling API")
        void cachedResultReturned() {
            var cachedJson = """
                {"status":"VERIFIED","tier":"KYC_TIER_2","cachedAtMs":1709856000000}
                """;
            given(valueOps.get(anyString())).willReturn(cachedJson);

            var result = adapter.verify(UUID.randomUUID(), UUID.randomUUID());

            var expected = KycResult.builder()
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("onfido")
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("kycResultId", "checkId", "providerRef", "checkedAt")
                    .isEqualTo(expected);

            // No API calls — all from cache
            wireMock.verify(0, getRequestedFor(urlPathEqualTo("/v3.6/checks")));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when API returns server error")
        void serverError() {
            given(valueOps.get(anyString())).willReturn(null);

            wireMock.stubFor(get(urlPathEqualTo("/v3.6/checks"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> adapter.verify(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when API returns 401 unauthorized")
        void unauthorizedError() {
            given(valueOps.get(anyString())).willReturn(null);

            wireMock.stubFor(get(urlPathEqualTo("/v3.6/checks"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> adapter.verify(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(Exception.class);
        }
    }
}
