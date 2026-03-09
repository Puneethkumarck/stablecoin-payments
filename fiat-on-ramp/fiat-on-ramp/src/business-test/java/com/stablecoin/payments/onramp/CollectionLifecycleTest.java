package com.stablecoin.payments.onramp;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentRequest;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspRefundRequest;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import com.stablecoin.payments.onramp.domain.service.CollectionCommandHandler;
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
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aFailedEventJson;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aSucceededEventJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the collection order lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * a controllable {@link PspGateway} that returns predictable pspReferences,
 * and the fallback webhook signature validator (always valid).
 * <p>
 * Each scenario exercises the full flow through REST endpoints using MockMvc.
 */
@DisplayName("Collection Lifecycle — Business Tests")
@ContextConfiguration(classes = CollectionLifecycleTest.TestPspConfig.class)
class CollectionLifecycleTest extends AbstractBusinessTest {

    private static final String KNOWN_PSP_REFERENCE = "pi_test_business_001";
    private static final String KNOWN_REFUND_REFERENCE = "re_test_business_001";

    @TestConfiguration
    static class TestPspConfig {

        @Bean
        @Primary
        public PspGateway testPspGateway() {
            return new PspGateway() {
                @Override
                public PspPaymentResult initiatePayment(PspPaymentRequest request) {
                    return new PspPaymentResult(KNOWN_PSP_REFERENCE, "succeeded");
                }

                @Override
                public PspRefundResult initiateRefund(PspRefundRequest request) {
                    return new PspRefundResult(KNOWN_REFUND_REFERENCE, "succeeded");
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CollectionOrderRepository collectionOrderRepository;

    @Autowired
    private CollectionCommandHandler collectionCommandHandler;

    // ── Helpers ──────────────────────────────────────────────────────────

    private String collectionRequestJson(UUID paymentId, UUID correlationId) {
        return """
                {
                  "paymentId": "%s",
                  "correlationId": "%s",
                  "amount": 1000.00,
                  "currency": "USD",
                  "paymentRailType": "SEPA",
                  "railCountry": "DE",
                  "railCurrency": "EUR",
                  "pspId": "stripe_001",
                  "pspName": "Stripe",
                  "senderAccountHash": "sha256_abc123def456",
                  "senderBankCode": "DEUTDEFF",
                  "senderAccountType": "IBAN",
                  "senderAccountCountry": "DE"
                }
                """.formatted(paymentId, correlationId);
    }

    private String refundRequestJson() {
        return """
                {
                  "refundAmount": 1000.00,
                  "currency": "USD",
                  "reason": "Customer requested refund"
                }
                """;
    }

    private static String extractField(String jsonResponse, String fieldName) {
        var pattern = "\"" + fieldName + "\":\"";
        var start = jsonResponse.indexOf(pattern) + pattern.length();
        var end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }

    /**
     * Creates a collection order via POST and returns the collectionId.
     */
    private String createCollectionOrder(UUID paymentId, UUID correlationId) throws Exception {
        var result = mockMvc.perform(post("/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionRequestJson(paymentId, correlationId)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractField(result.getResponse().getContentAsString(), "collectionId");
    }

    /**
     * Sends a Stripe webhook for a succeeded payment intent.
     */
    private void sendSucceededWebhook(String pspRef, long amountCents,
                                       String currency, String collectionId) throws Exception {
        var rawBody = aSucceededEventJson(pspRef, amountCents, currency,
                UUID.fromString(collectionId));

        mockMvc.perform(post("/internal/webhooks/psp/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1234567890,v1=dummy")
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    /**
     * Sends a Stripe webhook for a failed payment intent.
     */
    private void sendFailedWebhook(String pspRef, String collectionId) throws Exception {
        var rawBody = aFailedEventJson(pspRef, UUID.fromString(collectionId));

        mockMvc.perform(post("/internal/webhooks/psp/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1234567890,v1=dummy")
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("received")));
    }

    // ── Scenarios ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path — POST collection -> webhook succeeded -> COLLECTED")
    class HappyPath {

        @Test
        @DisplayName("should complete full collection lifecycle with outbox event")
        void shouldCompleteFullCollectionLifecycle() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. POST /v1/collections -> 201 CREATED
            var collectionId = createCollectionOrder(paymentId, correlationId);

            // 2. GET -> verify AWAITING_CONFIRMATION
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.collectionId", is(collectionId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("AWAITING_CONFIRMATION")))
                    .andExpect(jsonPath("$.pspReference", is(KNOWN_PSP_REFERENCE)))
                    .andExpect(jsonPath("$.psp", is("Stripe")))
                    .andExpect(jsonPath("$.paymentRail", is("SEPA")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andExpect(jsonPath("$.expiresAt", notNullValue()));

            // 3. Webhook: payment_intent.succeeded with matching pspReference and amount
            sendSucceededWebhook(KNOWN_PSP_REFERENCE, 100000L, "usd", collectionId);

            // 4. GET -> verify COLLECTED
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTED")))
                    .andExpect(jsonPath("$.estimatedSettlementAt", notNullValue()));

            // 5. Verify outbox events (initiated + completed)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM onramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Collection Failure — webhook with failure -> COLLECTION_FAILED")
    class CollectionFailure {

        @Test
        @DisplayName("should transition to COLLECTION_FAILED when PSP reports failure")
        void shouldTransitionToCollectionFailed() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create collection
            var collectionId = createCollectionOrder(paymentId, correlationId);

            // 2. Webhook: payment_intent.payment_failed
            sendFailedWebhook(KNOWN_PSP_REFERENCE, collectionId);

            // 3. GET -> verify COLLECTION_FAILED
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTION_FAILED")));

            // 4. Verify outbox events (initiated + failed)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM onramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Amount Mismatch — webhook with wrong amount -> AMOUNT_MISMATCH")
    class AmountMismatch {

        @Test
        @DisplayName("should detect amount mismatch when webhook amount differs from order")
        void shouldDetectAmountMismatch() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create collection (amount = 1000.00 USD = 100000 cents)
            var collectionId = createCollectionOrder(paymentId, correlationId);

            // 2. Webhook: payment_intent.succeeded with WRONG amount (500.00 = 50000 cents)
            sendSucceededWebhook(KNOWN_PSP_REFERENCE, 50000L, "usd", collectionId);

            // 3. GET -> verify AMOUNT_MISMATCH
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("AMOUNT_MISMATCH")));

            // 4. Verify outbox events (initiated + failed/mismatch)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM onramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Refund Compensation — COLLECTED -> refund -> REFUNDED")
    class RefundCompensation {

        @Test
        @DisplayName("should complete refund lifecycle with outbox event")
        void shouldCompleteRefundLifecycle() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create collection + webhook confirm -> COLLECTED
            var collectionId = createCollectionOrder(paymentId, correlationId);
            sendSucceededWebhook(KNOWN_PSP_REFERENCE, 100000L, "usd", collectionId);

            // Verify COLLECTED
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTED")));

            // 2. POST /v1/collections/{collectionId}/refunds -> 201 CREATED
            mockMvc.perform(post("/v1/collections/{collectionId}/refunds", collectionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refundRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.refundId", notNullValue()))
                    .andExpect(jsonPath("$.collectionId", is(collectionId)))
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.refundAmount", is(1000.00)))
                    .andExpect(jsonPath("$.currency", is("USD")));

            // 3. GET collection -> verify REFUNDED
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("REFUNDED")));

            // 4. Verify outbox events (initiated + collected + refund = 3+)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM onramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Idempotency — same paymentId returns 200 OK")
    class Idempotency {

        @Test
        @DisplayName("should return 200 OK for duplicate paymentId with same collectionId")
        void shouldReturn200ForDuplicatePaymentId() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var requestBody = collectionRequestJson(paymentId, correlationId);

            // 1. First request -> 201 CREATED
            var firstResult = mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andReturn();

            var firstCollectionId = extractField(
                    firstResult.getResponse().getContentAsString(), "collectionId");

            // 2. Second request with same paymentId -> 200 OK
            var secondResult = mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andReturn();

            var secondCollectionId = extractField(
                    secondResult.getResponse().getContentAsString(), "collectionId");

            // 3. Both responses reference the same collectionId
            assertThat(secondCollectionId).isEqualTo(firstCollectionId);
        }
    }

    @Nested
    @DisplayName("Expired Collection — past expiresAt -> COLLECTION_FAILED")
    class ExpiredCollection {

        @Test
        @DisplayName("should expire collection when expiresAt is in the past")
        void shouldExpireCollectionWhenPastExpiresAt() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create collection
            var collectionId = createCollectionOrder(paymentId, correlationId);

            // 2. Verify AWAITING_CONFIRMATION
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("AWAITING_CONFIRMATION")));

            // 3. Set expiresAt to the past via JdbcTemplate (simulates time passing)
            jdbcTemplate.update(
                    "UPDATE collection_orders SET expires_at = ? WHERE collection_id = ?::uuid",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),
                    collectionId);

            // 4. Query for expired orders (same as CollectionExpiryJob does)
            var now = Instant.now();
            var expiredOrders = collectionOrderRepository.findExpiredByStatus(
                    CollectionStatus.AWAITING_CONFIRMATION, now);
            assertThat(expiredOrders).hasSize(1);

            // 5. Expire via CollectionCommandHandler (same path as CollectionExpiryJob)
            collectionCommandHandler.expireCollection(expiredOrders.getFirst(), now);

            // 6. GET -> verify COLLECTION_FAILED
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTION_FAILED")));

            // 7. Verify outbox events (initiated + expired/failed)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM onramp_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Webhook Idempotency — duplicate webhook is ignored")
    class WebhookIdempotency {

        @Test
        @DisplayName("should ignore duplicate succeeded webhook for already-collected order")
        void shouldIgnoreDuplicateSucceededWebhook() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Create collection + first webhook -> COLLECTED
            var collectionId = createCollectionOrder(paymentId, correlationId);
            sendSucceededWebhook(KNOWN_PSP_REFERENCE, 100000L, "usd", collectionId);

            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTED")));

            // 2. Send same webhook again -> should still return 200 (idempotent)
            sendSucceededWebhook(KNOWN_PSP_REFERENCE, 100000L, "usd", collectionId);

            // 3. Status should still be COLLECTED (not double-processed)
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COLLECTED")));
        }
    }
}
