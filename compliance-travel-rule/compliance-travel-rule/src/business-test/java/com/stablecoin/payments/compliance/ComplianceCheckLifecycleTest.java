package com.stablecoin.payments.compliance;

import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.compliance.application.filter.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the compliance check lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * mocked external providers ({@link KycProvider}, {@link SanctionsProvider}),
 * and real fallback adapters for AML and Travel Rule.
 */
@DisplayName("Compliance Check Lifecycle — Business Tests")
@ContextConfiguration(classes = ComplianceCheckLifecycleTest.MockProviderConfig.class)
class ComplianceCheckLifecycleTest extends AbstractBusinessTest {

    @TestConfiguration
    static class MockProviderConfig {

        @Bean
        @Primary
        public KycProvider mockKycProvider() {
            return mock(KycProvider.class);
        }

        @Bean
        @Primary
        public SanctionsProvider mockSanctionsProvider() {
            return mock(SanctionsProvider.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KycProvider kycProvider;

    @Autowired
    private SanctionsProvider sanctionsProvider;

    @BeforeEach
    void resetMocks() {
        reset(kycProvider, sanctionsProvider);
    }

    @Nested
    @DisplayName("Happy Path — full pipeline passes")
    class HappyPath {

        @Test
        @DisplayName("should complete compliance check with PASSED status and create outbox event")
        void shouldCompleteWithPassedStatus() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            given(kycProvider.verify(any(), any())).willReturn(KycResult.builder()
                    .kycResultId(UUID.randomUUID())
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("mock")
                    .providerRef("mock-kyc-ref")
                    .checkedAt(Instant.now())
                    .build());

            given(sanctionsProvider.screen(any(), any())).willReturn(SanctionsResult.builder()
                    .sanctionsResultId(UUID.randomUUID())
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(false)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("mock")
                    .providerRef("mock-sanctions-ref")
                    .screenedAt(Instant.now())
                    .build());

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 1000.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId, senderId, recipientId);

            // POST — initiate compliance check
            var createResult = mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("PASSED")))
                    .andExpect(jsonPath("$.overallResult", is("PASSED")))
                    .andExpect(jsonPath("$.kycResult.senderStatus", is("VERIFIED")))
                    .andExpect(jsonPath("$.kycResult.recipientStatus", is("VERIFIED")))
                    .andExpect(jsonPath("$.kycResult.senderKycTier", is("KYC_TIER_2")))
                    .andExpect(jsonPath("$.sanctionsResult.senderHit", is(false)))
                    .andExpect(jsonPath("$.sanctionsResult.recipientHit", is(false)))
                    .andExpect(jsonPath("$.riskScore.score", is(25)))
                    .andExpect(jsonPath("$.riskScore.band", is("LOW")))
                    .andExpect(jsonPath("$.riskScore.factors", hasItem("cross_border")))
                    .andExpect(jsonPath("$.riskScore.factors", hasItem("new_customer")))
                    .andExpect(jsonPath("$.travelRule.protocol", is("IVMS101")))
                    .andExpect(jsonPath("$.travelRule.transmissionStatus", is("TRANSMITTED")))
                    .andExpect(jsonPath("$.completedAt", notNullValue()))
                    .andExpect(jsonPath("$.errorCode", nullValue()))
                    .andExpect(jsonPath("$.errorMessage", nullValue()))
                    .andReturn();

            // Extract checkId for GET verification
            var responseBody = createResult.getResponse().getContentAsString();
            var checkId = extractCheckId(responseBody);

            // GET — verify persisted state matches
            mockMvc.perform(get("/v1/compliance/checks/{checkId}", checkId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkId", is(checkId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("PASSED")))
                    .andExpect(jsonPath("$.overallResult", is("PASSED")));

            // Verify outbox event was created for compliance.result
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compliance_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);

            // Verify providers were called
            then(kycProvider).should().verify(senderId, recipientId);
            then(sanctionsProvider).should().screen(senderId, recipientId);
        }
    }

    @Nested
    @DisplayName("Sanctions Hit — pipeline stops at sanctions screening")
    class SanctionsHitScenario {

        @Test
        @DisplayName("should terminate with SANCTIONS_HIT when sanctions screening detects a hit")
        void shouldTerminateWithSanctionsHit() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            given(kycProvider.verify(any(), any())).willReturn(KycResult.builder()
                    .kycResultId(UUID.randomUUID())
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("mock")
                    .providerRef("mock-kyc-ref")
                    .checkedAt(Instant.now())
                    .build());

            given(sanctionsProvider.screen(any(), any())).willReturn(SanctionsResult.builder()
                    .sanctionsResultId(UUID.randomUUID())
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(true)
                    .recipientHit(false)
                    .hitDetails("{\"list\":\"OFAC_SDN\"}")
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("mock")
                    .providerRef("mock-sanctions-ref")
                    .screenedAt(Instant.now())
                    .build());

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 5000.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId, senderId, recipientId);

            // POST — initiate check that hits sanctions
            var createResult = mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("SANCTIONS_HIT")))
                    .andExpect(jsonPath("$.overallResult", is("SANCTIONS_HIT")))
                    .andExpect(jsonPath("$.sanctionsResult.senderHit", is(true)))
                    .andExpect(jsonPath("$.sanctionsResult.recipientHit", is(false)))
                    .andExpect(jsonPath("$.errorCode", is("CO-2001")))
                    .andExpect(jsonPath("$.errorMessage", notNullValue()))
                    .andExpect(jsonPath("$.completedAt", notNullValue()))
                    .andExpect(jsonPath("$.riskScore", nullValue()))
                    .andExpect(jsonPath("$.travelRule", nullValue()))
                    .andReturn();

            var checkId = extractCheckId(createResult.getResponse().getContentAsString());

            // GET — verify persisted state
            mockMvc.perform(get("/v1/compliance/checks/{checkId}", checkId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SANCTIONS_HIT")))
                    .andExpect(jsonPath("$.overallResult", is("SANCTIONS_HIT")));

            // Verify outbox events: ComplianceCheckFailed + SanctionsHitEvent (2 events)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compliance_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("KYC Failure — pipeline stops at KYC verification")
    class KycFailureScenario {

        @Test
        @DisplayName("should terminate with FAILED when KYC returns REJECTED")
        void shouldTerminateWithFailedWhenKycRejected() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            given(kycProvider.verify(any(), any())).willReturn(KycResult.builder()
                    .kycResultId(UUID.randomUUID())
                    .senderKycTier(KycTier.KYC_TIER_1)
                    .senderStatus(KycStatus.REJECTED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("mock")
                    .providerRef("mock-kyc-ref")
                    .checkedAt(Instant.now())
                    .build());

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 2000.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId, senderId, recipientId);

            // POST — initiate check that fails KYC
            var createResult = mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("FAILED")))
                    .andExpect(jsonPath("$.overallResult", is("FAILED")))
                    .andExpect(jsonPath("$.kycResult.senderStatus", is("REJECTED")))
                    .andExpect(jsonPath("$.errorCode", is("CO-1001")))
                    .andExpect(jsonPath("$.errorMessage", is("KYC verification failed")))
                    .andExpect(jsonPath("$.completedAt", notNullValue()))
                    .andExpect(jsonPath("$.sanctionsResult", nullValue()))
                    .andExpect(jsonPath("$.riskScore", nullValue()))
                    .andExpect(jsonPath("$.travelRule", nullValue()))
                    .andReturn();

            var checkId = extractCheckId(createResult.getResponse().getContentAsString());

            // GET — verify persisted state
            mockMvc.perform(get("/v1/compliance/checks/{checkId}", checkId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("FAILED")))
                    .andExpect(jsonPath("$.overallResult", is("FAILED")))
                    .andExpect(jsonPath("$.errorCode", is("CO-1001")));

            // Sanctions provider should NOT have been called (pipeline stopped at KYC)
            then(sanctionsProvider).shouldHaveNoInteractions();

            // Verify outbox event for ComplianceCheckFailed
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compliance_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Same Sender — two payments for the same customer")
    class SameSenderScenario {

        @Test
        @DisplayName("should invoke KYC provider independently for each payment")
        void shouldCallKycProviderForEachPayment() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var paymentId1 = UUID.randomUUID();
            var paymentId2 = UUID.randomUUID();

            given(kycProvider.verify(any(), any())).willReturn(KycResult.builder()
                    .kycResultId(UUID.randomUUID())
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("mock")
                    .providerRef("mock-kyc-ref")
                    .checkedAt(Instant.now())
                    .build());

            given(sanctionsProvider.screen(any(), any())).willReturn(SanctionsResult.builder()
                    .sanctionsResultId(UUID.randomUUID())
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(false)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("mock")
                    .providerRef("mock-sanctions-ref")
                    .screenedAt(Instant.now())
                    .build());

            var requestBody1 = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 1500.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId1, senderId, recipientId);

            var requestBody2 = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 2000.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId2, senderId, recipientId);

            // First payment
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody1))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status", is("PASSED")))
                    .andExpect(jsonPath("$.overallResult", is("PASSED")))
                    .andExpect(jsonPath("$.riskScore.factors", hasItem("new_customer")));

            // Second payment for same sender — both checks should pass,
            // KYC provider is called again (no provider-level caching in current implementation)
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody2))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status", is("PASSED")))
                    .andExpect(jsonPath("$.overallResult", is("PASSED")))
                    .andExpect(jsonPath("$.riskScore.factors", hasItem("new_customer")));

            // Verify KYC and sanctions providers were each called twice (once per payment)
            then(kycProvider).should(times(2)).verify(senderId, recipientId);
            then(sanctionsProvider).should(times(2)).screen(senderId, recipientId);

            // Verify outbox events for both payments
            var outboxCount1 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compliance_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId1.toString());
            var outboxCount2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM compliance_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId2.toString());
            assertThat(outboxCount1).isGreaterThanOrEqualTo(1);
            assertThat(outboxCount2).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Idempotency — duplicate paymentId is rejected")
    class IdempotencyScenario {

        @Test
        @DisplayName("should return 409 Conflict with CO-1002 for duplicate paymentId")
        void shouldRejectDuplicatePayment() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

            given(kycProvider.verify(any(), any())).willReturn(KycResult.builder()
                    .kycResultId(UUID.randomUUID())
                    .senderKycTier(KycTier.KYC_TIER_2)
                    .senderStatus(KycStatus.VERIFIED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .provider("mock")
                    .providerRef("mock-kyc-ref")
                    .checkedAt(Instant.now())
                    .build());

            given(sanctionsProvider.screen(any(), any())).willReturn(SanctionsResult.builder()
                    .sanctionsResultId(UUID.randomUUID())
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(false)
                    .listsChecked(List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN"))
                    .provider("mock")
                    .providerRef("mock-sanctions-ref")
                    .screenedAt(Instant.now())
                    .build());

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 1000.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(paymentId, senderId, recipientId);

            // First request succeeds
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status", is("PASSED")));

            // Second request with same paymentId returns 409 Conflict
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("CO-1002")))
                    .andExpect(jsonPath("$.status", is("Conflict")))
                    .andExpect(jsonPath("$.message", notNullValue()));

            // KYC and sanctions providers called only once (second request rejected before pipeline)
            then(kycProvider).should(times(1)).verify(senderId, recipientId);
            then(sanctionsProvider).should(times(1)).screen(senderId, recipientId);
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private static String extractCheckId(String jsonResponse) {
        var pattern = "\"checkId\":\"";
        var start = jsonResponse.indexOf(pattern) + pattern.length();
        var end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
