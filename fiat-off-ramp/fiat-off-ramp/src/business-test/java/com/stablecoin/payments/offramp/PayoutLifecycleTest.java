package com.stablecoin.payments.offramp;

import com.stablecoin.payments.offramp.domain.exception.RedemptionFailedException;
import com.stablecoin.payments.offramp.domain.port.PayoutPartnerGateway;
import com.stablecoin.payments.offramp.domain.port.PayoutResult;
import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import com.stablecoin.payments.offramp.domain.service.PayoutMonitorCommandHandler;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.stablecoin.payments.offramp.application.filter.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the payout order lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * controllable {@link RedemptionGateway} and {@link PayoutPartnerGateway},
 * and the fallback webhook signature validator (always valid).
 * <p>
 * Each scenario exercises the full flow through REST endpoints using MockMvc.
 */
@DisplayName("Payout Lifecycle — Business Tests")
@ContextConfiguration(classes = PayoutLifecycleTest.TestPortsConfig.class)
class PayoutLifecycleTest extends AbstractBusinessTest {

    private static final String KNOWN_PARTNER_REFERENCE = "dev-payout-bt-001";
    private static final String KNOWN_REDEMPTION_REFERENCE = "dev-redeem-bt-001";
    private static final BigDecimal REDEEMED_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal APPLIED_FX_RATE = new BigDecimal("0.92");
    private static final BigDecimal EXPECTED_FIAT_AMOUNT = new BigDecimal("920.00");

    /**
     * Controllable behaviour reference — tests can swap redemption behaviour.
     */
    private static final AtomicReference<RedemptionGateway> REDEMPTION_BEHAVIOUR =
            new AtomicReference<>();

    @TestConfiguration
    static class TestPortsConfig {

        @Bean
        @Primary
        public RedemptionGateway testRedemptionGateway() {
            return request -> {
                var behaviour = REDEMPTION_BEHAVIOUR.get();
                if (behaviour != null) {
                    return behaviour.redeem(request);
                }
                return new RedemptionResult(
                        KNOWN_REDEMPTION_REFERENCE,
                        request.amount().multiply(APPLIED_FX_RATE),
                        "EUR",
                        Instant.now());
            };
        }

        @Bean
        @Primary
        public PayoutPartnerGateway testPayoutPartnerGateway() {
            return request -> new PayoutResult(
                    KNOWN_PARTNER_REFERENCE,
                    "PROCESSING",
                    null);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayoutMonitorCommandHandler payoutMonitorCommandHandler;

    @BeforeEach
    void resetBehaviour() {
        REDEMPTION_BEHAVIOUR.set(null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String payoutRequestJson(UUID paymentId, UUID correlationId, String payoutType) {
        return """
                {
                  "paymentId": "%s",
                  "correlationId": "%s",
                  "transferId": "%s",
                  "payoutType": "%s",
                  "stablecoin": "USDC",
                  "redeemedAmount": %s,
                  "targetCurrency": "EUR",
                  "appliedFxRate": %s,
                  "recipientId": "%s",
                  "recipientAccountHash": "sha256_recipient_bt",
                  "paymentRail": "SEPA",
                  "offRampPartnerId": "modulr_001",
                  "offRampPartnerName": "Modulr",
                  "bankAccountNumber": "DE89370400440532013000",
                  "bankCode": "DEUTDEFF",
                  "bankAccountType": "IBAN",
                  "bankAccountCountry": "DE"
                }
                """.formatted(paymentId, correlationId, UUID.randomUUID(),
                payoutType, REDEEMED_AMOUNT, APPLIED_FX_RATE, UUID.randomUUID());
    }

    private String fiatPayoutRequestJson(UUID paymentId, UUID correlationId) {
        return payoutRequestJson(paymentId, correlationId, "FIAT");
    }

    private String holdPayoutRequestJson(UUID paymentId, UUID correlationId) {
        return payoutRequestJson(paymentId, correlationId, "HOLD_STABLECOIN");
    }

    private static String extractField(String jsonResponse, String fieldName) {
        var pattern = "\"" + fieldName + "\":\"";
        var start = jsonResponse.indexOf(pattern) + pattern.length();
        var end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }

    /**
     * Creates a FIAT payout order via POST and returns the payoutId.
     */
    private String createFiatPayout(UUID paymentId, UUID correlationId) throws Exception {
        var result = mockMvc.perform(post("/v1/payouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .content(fiatPayoutRequestJson(paymentId, correlationId)))
                .andExpect(status().isAccepted())
                .andReturn();

        return extractField(result.getResponse().getContentAsString(), "payoutId");
    }

    /**
     * Sends a partner settlement webhook for a given partnerReference.
     */
    private void sendSettlementWebhook(String partnerReference) throws Exception {
        var eventId = "evt_" + UUID.randomUUID();
        var rawBody = """
                {
                    "event_id": "%s",
                    "event_type": "payment.settled",
                    "payment_reference": "%s",
                    "amount": "%s",
                    "currency": "EUR",
                    "status": "SETTLED",
                    "settled_at": "%s"
                }
                """.formatted(eventId, partnerReference,
                EXPECTED_FIAT_AMOUNT.toPlainString(), Instant.now().toString());

        mockMvc.perform(post("/internal/webhooks/partner/modulr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    /**
     * Sends a partner failure webhook for a given partnerReference.
     */
    private void sendFailureWebhook(String partnerReference) throws Exception {
        var eventId = "evt_" + UUID.randomUUID();
        var rawBody = """
                {
                    "event_id": "%s",
                    "event_type": "payment.failed",
                    "payment_reference": "%s",
                    "amount": "%s",
                    "currency": "EUR",
                    "status": "FAILED",
                    "failure_reason": "Insufficient funds in beneficiary account"
                }
                """.formatted(eventId, partnerReference,
                EXPECTED_FIAT_AMOUNT.toPlainString());

        mockMvc.perform(post("/internal/webhooks/partner/modulr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    // ── Scenarios ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path FIAT — POST payout -> REDEEMING -> REDEEMED -> PAYOUT_INITIATED -> webhook -> COMPLETED")
    class HappyPathFiat {

        @Test
        @DisplayName("should complete full FIAT payout lifecycle with outbox events")
        void shouldCompleteFullFiatPayoutLifecycle() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. POST /v1/payouts -> 202 ACCEPTED (PAYOUT_INITIATED after redemption + partner call)
            var payoutId = createFiatPayout(paymentId, correlationId);

            // 2. GET -> verify PAYOUT_INITIATED
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payoutId", is(payoutId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("PAYOUT_INITIATED")))
                    .andExpect(jsonPath("$.payoutType", is("FIAT")))
                    .andExpect(jsonPath("$.paymentRail", is("SEPA")))
                    .andExpect(jsonPath("$.partner", is("Modulr")))
                    .andExpect(jsonPath("$.partnerReference", is(KNOWN_PARTNER_REFERENCE)))
                    .andExpect(jsonPath("$.fiatAmount", is(920.00)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));

            // 3. Webhook: payment.settled
            sendSettlementWebhook(KNOWN_PARTNER_REFERENCE);

            // 4. GET -> verify COMPLETED
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.partnerSettledAt", notNullValue()));

            // 5. Verify outbox events (StablecoinRedeemed + FiatPayoutInitiated + FiatPayoutCompleted = 3+)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM offramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("HOLD_STABLECOIN — POST -> STABLECOIN_HELD -> COMPLETED")
    class HoldStablecoin {

        @Test
        @DisplayName("should complete hold stablecoin path without redemption or partner payout")
        void shouldCompleteHoldStablecoinPath() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. POST /v1/payouts with HOLD_STABLECOIN -> 202 ACCEPTED
            var result = mockMvc.perform(post("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(holdPayoutRequestJson(paymentId, correlationId)))
                    .andExpect(status().isAccepted())
                    .andReturn();

            var payoutId = extractField(result.getResponse().getContentAsString(), "payoutId");

            // 2. GET -> verify COMPLETED (hold path goes PENDING -> STABLECOIN_HELD -> COMPLETED immediately)
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payoutId", is(payoutId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.payoutType", is("HOLD_STABLECOIN")));

            // 3. Verify no redemption records were created (hold skips redemption)
            var redemptionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stablecoin_redemptions WHERE payout_id = ?::uuid",
                    Integer.class, payoutId);
            assertThat(redemptionCount).isZero();
        }
    }

    @Nested
    @DisplayName("Redemption Failure — Circle fails -> REDEMPTION_FAILED -> MANUAL_REVIEW via exception")
    class RedemptionFailure {

        @Test
        @DisplayName("should return 422 when Circle redemption fails")
        void shouldReturn422WhenRedemptionFails() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Override redemption gateway to throw
            REDEMPTION_BEHAVIOUR.set(request -> {
                throw new RedemptionFailedException(request.payoutId(), "Circle API error: insufficient USDC balance");
            });

            // POST /v1/payouts -> 422 Unprocessable Entity
            mockMvc.perform(post("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(fiatPayoutRequestJson(paymentId, correlationId)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code", is("FR-2002")));

            // Verify no payout order was persisted (transaction rolled back)
            var orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payout_orders WHERE payment_id = ?::uuid",
                    Integer.class, paymentId.toString());
            assertThat(orderCount).isZero();
        }
    }

    @Nested
    @DisplayName("Payout Failure — partner webhook reports failure -> PAYOUT_FAILED")
    class PayoutFailure {

        @Test
        @DisplayName("should transition to PAYOUT_FAILED when partner reports failure via webhook")
        void shouldTransitionToPayoutFailedOnPartnerFailure() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create FIAT payout -> PAYOUT_INITIATED
            var payoutId = createFiatPayout(paymentId, correlationId);

            // 2. Webhook: payment.failed
            sendFailureWebhook(KNOWN_PARTNER_REFERENCE);

            // 3. GET -> verify PAYOUT_FAILED
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("PAYOUT_FAILED")));

            // 4. Verify outbox events (StablecoinRedeemed + FiatPayoutInitiated + FiatPayoutFailed = 3+)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM offramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Idempotency — same paymentId returns 200 OK")
    class Idempotency {

        @Test
        @DisplayName("should return 200 OK for duplicate paymentId with same payoutId")
        void shouldReturn200ForDuplicatePaymentId() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var requestBody = fiatPayoutRequestJson(paymentId, correlationId);

            // 1. First request -> 202 ACCEPTED
            var firstResult = mockMvc.perform(post("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andReturn();

            var firstPayoutId = extractField(
                    firstResult.getResponse().getContentAsString(), "payoutId");

            // 2. Second request with same paymentId -> 200 OK
            var secondResult = mockMvc.perform(post("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andReturn();

            var secondPayoutId = extractField(
                    secondResult.getResponse().getContentAsString(), "payoutId");

            // 3. Both responses reference the same payoutId
            assertThat(secondPayoutId).isEqualTo(firstPayoutId);
        }
    }

    @Nested
    @DisplayName("Stuck Payout Detection — simulate timeout -> escalation to MANUAL_REVIEW")
    class StuckPayoutDetection {

        @Test
        @DisplayName("should escalate stuck PAYOUT_INITIATED order to MANUAL_REVIEW")
        void shouldEscalateStuckPayoutToManualReview() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create FIAT payout -> PAYOUT_INITIATED
            var payoutId = createFiatPayout(paymentId, correlationId);

            // 2. Verify PAYOUT_INITIATED
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("PAYOUT_INITIATED")));

            // 3. Set updated_at to 3 hours ago (exceeds 120-minute threshold)
            jdbcTemplate.update(
                    "UPDATE payout_orders SET updated_at = ? WHERE payout_id = ?::uuid",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(10800)),
                    payoutId);

            // 4. Run stuck payout detection (same path as PayoutMonitorJob)
            payoutMonitorCommandHandler.detectAndEscalateStuckPayouts();

            // 5. GET -> verify MANUAL_REVIEW (failPayout -> escalateToManualReview)
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("MANUAL_REVIEW")));

            // 6. Verify outbox events (StablecoinRedeemed + FiatPayoutInitiated + FiatPayoutFailed = 3+)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM offramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Webhook Idempotency — duplicate webhook is ignored")
    class WebhookIdempotency {

        @Test
        @DisplayName("should ignore duplicate settlement webhook for already-completed order")
        void shouldIgnoreDuplicateSettlementWebhook() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create FIAT payout + settlement webhook -> COMPLETED
            var payoutId = createFiatPayout(paymentId, correlationId);
            sendSettlementWebhook(KNOWN_PARTNER_REFERENCE);

            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")));

            // 2. Send same webhook again -> should still return 200 (idempotent)
            sendSettlementWebhook(KNOWN_PARTNER_REFERENCE);

            // 3. Status should still be COMPLETED (not double-processed)
            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")));
        }
    }
}
