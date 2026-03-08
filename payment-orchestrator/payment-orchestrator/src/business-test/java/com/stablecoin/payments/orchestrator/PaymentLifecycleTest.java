package com.stablecoin.payments.orchestrator;

import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.client.ComplianceCheckClient;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.client.FxEngineClient;
import com.stablecoin.payments.orchestrator.domain.workflow.PaymentWorkflow;
import com.stablecoin.payments.orchestrator.domain.workflow.PaymentWorkflowImpl;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.ComplianceCheckActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.EventPublishingActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.application.config.TemporalConfig.TASK_QUEUE;
import static com.stablecoin.payments.orchestrator.application.filter.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult.PaymentResultStatus.COMPLETED;
import static com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentResult.PaymentResultStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the payment lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * Temporal {@link TestWorkflowEnvironment} with real activity beans,
 * and mocked downstream services (S2 Compliance, S6 FX Engine).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes = PaymentLifecycleTest.MockServicesConfig.class)
@TestPropertySource(properties = "app.temporal.client.enabled=false")
@DisplayName("Payment Lifecycle — Business Tests")
class PaymentLifecycleTest extends AbstractBusinessTest {

    private static final TestWorkflowEnvironment TEST_ENV = TestWorkflowEnvironment.newInstance();

    @TestConfiguration
    static class MockServicesConfig {

        @Bean
        @Primary
        public WorkflowClient testWorkflowClient() {
            return TEST_ENV.getWorkflowClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ComplianceCheckClient complianceCheckClient;

    @MockitoBean
    private FxEngineClient fxEngineClient;

    @Autowired
    private ComplianceCheckActivity complianceCheckActivity;

    @Autowired
    private FxLockActivity fxLockActivity;

    @Autowired
    private EventPublishingActivity eventPublishingActivity;

    @BeforeAll
    void setupTemporal() {
        var worker = TEST_ENV.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                complianceCheckActivity, fxLockActivity, eventPublishingActivity);
        TEST_ENV.start();
    }

    @AfterAll
    void teardownTemporal() {
        TEST_ENV.close();
    }

    @BeforeEach
    void resetMocks() {
        reset(complianceCheckClient, fxEngineClient);
    }

    // ── Happy Path ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path — compliance passes, FX rate locked, workflow completes")
    class HappyPath {

        @Test
        @DisplayName("should initiate payment, run saga, and complete workflow successfully")
        void shouldCompletePaymentSaga() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();
            var checkId = UUID.randomUUID();
            var quoteId = UUID.randomUUID();
            var lockId = UUID.randomUUID();

            // Mock S2 — compliance passes immediately
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(passedComplianceResponse(checkId));

            // Mock S6 — quote + lock succeeds
            given(fxEngineClient.getQuote(any(), any(), any()))
                    .willReturn(quoteResponse(quoteId));
            given(fxEngineClient.lockRate(any(), any()))
                    .willReturn(lockResponse(lockId, quoteId));

            // POST /v1/payments → 201 Created
            var result = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(paymentRequestBody(senderId, recipientId, "1000.00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andExpect(jsonPath("$.paymentId", notNullValue()))
                    .andExpect(jsonPath("$.senderId", is(senderId.toString())))
                    .andExpect(jsonPath("$.recipientId", is(recipientId.toString())))
                    .andReturn();

            var paymentId = extractPaymentId(result.getResponse().getContentAsString());

            // Wait for Temporal workflow to complete
            var workflowResult = waitForWorkflow(paymentId);
            assertThat(workflowResult.status()).isEqualTo(COMPLETED);

            // Verify payment persisted in DB
            var dbState = jdbcTemplate.queryForObject(
                    "SELECT state FROM payments WHERE payment_id = ?::uuid",
                    String.class, paymentId);
            assertThat(dbState).isEqualTo("INITIATED");

            // Verify PaymentInitiated outbox event
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orchestrator_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId);
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);

            // GET /v1/payments/{id} — verify retrieval
            mockMvc.perform(get("/v1/payments/{id}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId)))
                    .andExpect(jsonPath("$.state", is("INITIATED")));

            // Verify downstream services were called
            then(complianceCheckClient).should().initiateCheck(any());
            then(fxEngineClient).should().getQuote(any(), any(), any());
            then(fxEngineClient).should().lockRate(any(), any());
        }
    }

    // ── Compliance Rejection ────────────────────────────────────────

    @Nested
    @DisplayName("Compliance Rejection — sanctions hit stops workflow")
    class ComplianceRejection {

        @Test
        @DisplayName("should fail workflow when compliance returns SANCTIONS_HIT")
        void shouldFailOnSanctionsHit() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();
            var checkId = UUID.randomUUID();

            // Mock S2 — sanctions hit
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(sanctionsHitResponse(checkId));

            // POST /v1/payments → 201 Created
            var result = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(paymentRequestBody(senderId, recipientId, "5000.00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andReturn();

            var paymentId = extractPaymentId(result.getResponse().getContentAsString());

            // Wait for workflow to complete (with failure)
            var workflowResult = waitForWorkflow(paymentId);
            assertThat(workflowResult.status()).isEqualTo(FAILED);

            // Verify outbox: PaymentInitiated + PaymentFailed events
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orchestrator_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId);
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);

            // FX engine should NOT have been called (pipeline stopped at compliance)
            then(fxEngineClient).shouldHaveNoInteractions();

            // Compliance was called
            then(complianceCheckClient).should().initiateCheck(any());
        }
    }

    // ── FX Lock Failure ─────────────────────────────────────────────

    @Nested
    @DisplayName("FX Lock Failure — insufficient liquidity fails workflow")
    class FxLockFailure {

        @Test
        @DisplayName("should fail workflow when FX lock returns insufficient liquidity")
        void shouldFailOnInsufficientLiquidity() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();
            var checkId = UUID.randomUUID();
            var quoteId = UUID.randomUUID();

            // Mock S2 — compliance passes
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(passedComplianceResponse(checkId));

            // Mock S6 — quote succeeds, lock fails with insufficient liquidity
            given(fxEngineClient.getQuote(any(), any(), any()))
                    .willReturn(quoteResponse(quoteId));
            given(fxEngineClient.lockRate(any(), any()))
                    .willThrow(new FeignException.UnprocessableEntity(
                            "Insufficient liquidity for USD/EUR pool",
                            dummyFeignRequest(), null, null));

            // POST /v1/payments → 201 Created
            var result = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(paymentRequestBody(senderId, recipientId, "1000.00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andReturn();

            var paymentId = extractPaymentId(result.getResponse().getContentAsString());

            // Wait for workflow to complete (with failure)
            var workflowResult = waitForWorkflow(paymentId);
            assertThat(workflowResult.status()).isEqualTo(FAILED);

            // Verify outbox: PaymentInitiated + PaymentFailed events
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orchestrator_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId);
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);

            // Both S2 and S6 were called
            then(complianceCheckClient).should().initiateCheck(any());
            then(fxEngineClient).should().getQuote(any(), any(), any());
        }
    }

    // ── Cancel After FX Lock ────────────────────────────────────────

    @Nested
    @DisplayName("Cancel After FX Lock — compensation releases lock")
    class CancelAfterFxLock {

        @Test
        @DisplayName("should release FX lock when cancel signal is received after locking")
        void shouldReleaseFxLockOnCancel() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();
            var checkId = UUID.randomUUID();
            var quoteId = UUID.randomUUID();
            var lockId = UUID.randomUUID();

            // Mock S2 — compliance passes
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(passedComplianceResponse(checkId));

            // Mock S6 — quote succeeds
            given(fxEngineClient.getQuote(any(), any(), any()))
                    .willReturn(quoteResponse(quoteId));

            // Mock S6 — lock succeeds, but send cancel signal during lock
            given(fxEngineClient.lockRate(any(), any()))
                    .willAnswer(invocation -> {
                        // Extract paymentId from the lock request to send cancel signal
                        var lockRequest = invocation.getArgument(1,
                                com.stablecoin.payments.fx.api.request.FxRateLockRequest.class);
                        var paymentUuid = lockRequest.paymentId();

                        // Send cancel signal to workflow
                        var workflowStub = TEST_ENV.getWorkflowClient()
                                .newWorkflowStub(PaymentWorkflow.class,
                                        "payment-" + paymentUuid);
                        workflowStub.cancelPayment(new CancelRequest(
                                paymentUuid, "Customer requested cancellation", "API"));

                        return lockResponse(lockId, quoteId);
                    });

            // POST /v1/payments → 201 Created
            var result = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(paymentRequestBody(senderId, recipientId, "1000.00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andReturn();

            var paymentId = extractPaymentId(result.getResponse().getContentAsString());

            // Wait for workflow to complete (cancelled → FAILED)
            var workflowResult = waitForWorkflow(paymentId);
            assertThat(workflowResult.status()).isEqualTo(FAILED);
            assertThat(workflowResult.failureReason()).contains("Cancelled");

            // Verify outbox: PaymentInitiated + PaymentCompensationStarted events
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orchestrator_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId);
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);

            // Verify FX lock was released (compensation)
            then(fxEngineClient).should().releaseLock(lockId);
        }
    }

    // ── Idempotency ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — duplicate key returns existing payment")
    class Idempotency {

        @Test
        @DisplayName("should return 200 OK with existing payment on duplicate idempotency key")
        void shouldReturnExistingPaymentOnDuplicateKey() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();
            var checkId = UUID.randomUUID();
            var quoteId = UUID.randomUUID();
            var lockId = UUID.randomUUID();

            // Mock S2 + S6 for the first request
            given(complianceCheckClient.initiateCheck(any()))
                    .willReturn(passedComplianceResponse(checkId));
            given(fxEngineClient.getQuote(any(), any(), any()))
                    .willReturn(quoteResponse(quoteId));
            given(fxEngineClient.lockRate(any(), any()))
                    .willReturn(lockResponse(lockId, quoteId));

            var requestBody = paymentRequestBody(senderId, recipientId, "1000.00");

            // First request → 201 Created
            var firstResult = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            var paymentId = extractPaymentId(firstResult.getResponse().getContentAsString());

            // Wait for first workflow to complete
            waitForWorkflow(paymentId);

            // Second request with same idempotency key → 200 OK
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId)))
                    .andExpect(jsonPath("$.state", is("INITIATED")));

            // Verify only one payment exists in DB
            var paymentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE idempotency_key = ?",
                    Integer.class, idempotencyKey);
            assertThat(paymentCount).isEqualTo(1);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private PaymentResult waitForWorkflow(String paymentId) {
        return TEST_ENV.getWorkflowClient()
                .newUntypedWorkflowStub("payment-" + paymentId)
                .getResult(PaymentResult.class);
    }

    private static String extractPaymentId(String jsonResponse) {
        var pattern = "\"paymentId\":\"";
        var start = jsonResponse.indexOf(pattern) + pattern.length();
        var end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }

    private static String paymentRequestBody(UUID senderId, UUID recipientId, String amount) {
        return """
                {
                    "senderId": "%s",
                    "recipientId": "%s",
                    "sourceAmount": %s,
                    "sourceCurrency": "USD",
                    "targetCurrency": "EUR",
                    "sourceCountry": "US",
                    "targetCountry": "DE"
                }
                """.formatted(senderId, recipientId, amount);
    }

    private static ComplianceCheckResponse passedComplianceResponse(UUID checkId) {
        return new ComplianceCheckResponse(
                checkId, null, "PASSED", "PASSED",
                new ComplianceCheckResponse.RiskScoreResponse(25, "LOW", List.of("cross_border")),
                new ComplianceCheckResponse.KycResultResponse("VERIFIED", "VERIFIED", "KYC_TIER_2"),
                new ComplianceCheckResponse.SanctionsResultResponse(false, false, List.of("OFAC_SDN", "EU")),
                new ComplianceCheckResponse.TravelRuleResponse("IVMS101", "TRANSMITTED"),
                null, null, Instant.now(), Instant.now());
    }

    private static ComplianceCheckResponse sanctionsHitResponse(UUID checkId) {
        return new ComplianceCheckResponse(
                checkId, null, "SANCTIONS_HIT", "SANCTIONS_HIT",
                null, null,
                new ComplianceCheckResponse.SanctionsResultResponse(true, false, List.of("OFAC_SDN")),
                null, null, "Sanctions match detected",
                Instant.now(), Instant.now());
    }

    private static FxQuoteResponse quoteResponse(UUID quoteId) {
        return new FxQuoteResponse(
                quoteId, "USD", "EUR",
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                new BigDecimal("0.92"), new BigDecimal("1.0869"),
                50, new BigDecimal("5.00"), "refinitiv",
                Instant.now(), Instant.now().plusSeconds(300));
    }

    private static FxRateLockResponse lockResponse(UUID lockId, UUID quoteId) {
        return new FxRateLockResponse(
                lockId, quoteId, null,
                "USD", "EUR",
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                new BigDecimal("0.92"),
                50, new BigDecimal("5.00"),
                Instant.now(), Instant.now().plusSeconds(600));
    }

    private static Request dummyFeignRequest() {
        return Request.create(
                Request.HttpMethod.POST, "http://localhost",
                Collections.emptyMap(), null, new RequestTemplate());
    }
}
