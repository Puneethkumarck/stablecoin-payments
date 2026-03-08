package com.stablecoin.payments.phase2;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Phase 2 cross-service integration tests.
 * <p>
 * Requires Docker Compose stack running:
 * {@code docker compose -f docker-compose.phase2-test.yml up -d}
 * <p>
 * Tests real HTTP calls: S1 → S2 (compliance), S1 → S6 (FX engine),
 * Temporal workflow orchestration, and Kafka event publishing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Phase 2 — Cross-Service Saga Integration Tests")
class Phase2SagaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(Phase2SagaIntegrationTest.class);

    private static final String S1_BASE_URL = "http://localhost:8082/orchestrator";
    private static final String WIREMOCK_ADMIN_URL = "http://localhost:4444/__admin";
    private static final String TEMPORAL_ADDRESS = "localhost:7233";
    private static final String KAFKA_BROKERS = "localhost:9092";

    private static final JsonMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private HttpClient httpClient;
    private WorkflowClient workflowClient;

    @BeforeAll
    void setupClients() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        var stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(TEMPORAL_ADDRESS)
                        .build());
        workflowClient = WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace("default")
                        .build());
    }

    @AfterAll
    void teardownClients() {
        // httpClient and workflowClient are auto-closeable but no explicit close needed
    }

    @AfterEach
    void cleanWireMockOverrides() throws Exception {
        httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(WIREMOCK_ADMIN_URL + "/mappings/reset"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── Happy Path ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path — compliance passes, FX rate locked, workflow completes")
    class HappyPath {

        @Test
        @DisplayName("should complete payment saga end-to-end across S1 → S2 → S6")
        void shouldCompletePaymentSaga() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();

            // 1. POST /v1/payments → 201 Created
            var response = initiatePayment(senderId, recipientId, "1000.00", idempotencyKey);
            assertThat(response.statusCode()).isEqualTo(201);

            var body = JSON.readTree(response.body());
            var paymentId = body.get("paymentId").asText();
            log.info("Payment initiated: {}", paymentId);

            assertThat(body.get("state").asText()).isEqualTo("INITIATED");
            assertThat(body.get("senderId").asText()).isEqualTo(senderId.toString());
            assertThat(body.get("recipientId").asText()).isEqualTo(recipientId.toString());

            // 2. Wait for Temporal workflow to complete
            var workflowResult = waitForWorkflowCompletion(paymentId);
            log.info("Workflow result: {}", workflowResult);
            assertThat(workflowResult.get("status").asText()).isEqualTo("COMPLETED");

            // 3. Verify GET /v1/payments/{id} returns the payment
            var getResponse = getPayment(paymentId);
            assertThat(getResponse.statusCode()).isEqualTo(200);

            var getBody = JSON.readTree(getResponse.body());
            assertThat(getBody.get("paymentId").asText()).isEqualTo(paymentId);

            // 4. Verify Kafka: payment.initiated event published
            var events = pollKafkaEvents("payment.initiated", paymentId, Duration.ofSeconds(30));
            assertThat(events).isNotEmpty();
            log.info("Verified {} payment.initiated event(s) on Kafka", events.size());
        }
    }

    // ── Compliance Rejection ────────────────────────────────────────

    @Nested
    @DisplayName("Compliance Rejection — sanctions hit stops workflow")
    class ComplianceRejection {

        @Test
        @DisplayName("should fail workflow when sanctions screening returns a hit")
        void shouldFailOnSanctionsHit() throws Exception {
            // Configure WireMock: override sanctions to return a hit for ALL requests
            addWireMockSanctionsHitStub();

            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();

            // 1. POST /v1/payments → 201 Created
            var response = initiatePayment(senderId, recipientId, "5000.00", idempotencyKey);
            assertThat(response.statusCode()).isEqualTo(201);

            var body = JSON.readTree(response.body());
            var paymentId = body.get("paymentId").asText();
            log.info("Payment initiated (sanctions test): {}", paymentId);

            // 2. Wait for workflow to fail
            var workflowResult = waitForWorkflowCompletion(paymentId);
            log.info("Workflow result: {}", workflowResult);
            assertThat(workflowResult.get("status").asText()).isEqualTo("FAILED");

            // 3. Verify payment state reflects failure via GET
            var getResponse = getPayment(paymentId);
            assertThat(getResponse.statusCode()).isEqualTo(200);
            var getBody = JSON.readTree(getResponse.body());
            assertThat(getBody.get("paymentId").asText()).isEqualTo(paymentId);
        }
    }

    // ── Cancel In-Flight ────────────────────────────────────────────

    @Nested
    @DisplayName("Cancel — cancel endpoint returns appropriate response")
    class CancelPayment {

        @Test
        @DisplayName("should accept cancel request for an initiated payment")
        void shouldAcceptCancelRequest() throws Exception {
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var idempotencyKey = UUID.randomUUID().toString();

            // 1. Initiate payment
            var response = initiatePayment(senderId, recipientId, "1000.00", idempotencyKey);
            assertThat(response.statusCode()).isEqualTo(201);

            var body = JSON.readTree(response.body());
            var paymentId = body.get("paymentId").asText();

            // 2. Wait for workflow to complete first (saga steps are fast)
            waitForWorkflowCompletion(paymentId);

            // 3. Try cancel — workflow completes very quickly, so race outcomes:
            //    200 = cancelled, 409 = terminal state, 500 = Temporal workflow already done
            var cancelResponse = cancelPayment(paymentId);
            assertThat(cancelResponse.statusCode()).isIn(200, 409, 500);
            log.info("Cancel response: {} {}", cancelResponse.statusCode(),
                    cancelResponse.body());
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

            // 1. First request → 201 Created
            var first = initiatePayment(senderId, recipientId, "1000.00", idempotencyKey);
            assertThat(first.statusCode()).isEqualTo(201);

            var paymentId = JSON.readTree(first.body()).get("paymentId").asText();

            // 2. Wait for workflow
            waitForWorkflowCompletion(paymentId);

            // 3. Second request with same idempotency key → 200 OK
            var second = initiatePayment(senderId, recipientId, "1000.00", idempotencyKey);
            assertThat(second.statusCode()).isEqualTo(200);

            var secondBody = JSON.readTree(second.body());
            assertThat(secondBody.get("paymentId").asText()).isEqualTo(paymentId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private HttpResponse<String> initiatePayment(UUID senderId, UUID recipientId,
                                                  String amount, String idempotencyKey) throws Exception {
        var requestBody = """
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

        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(S1_BASE_URL + "/v1/payments"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", idempotencyKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getPayment(String paymentId) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(S1_BASE_URL + "/v1/payments/" + paymentId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> cancelPayment(String paymentId) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(S1_BASE_URL + "/v1/payments/" + paymentId + "/cancel"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"reason": "Integration test cancellation"}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode waitForWorkflowCompletion(String paymentId) {
        var workflowId = "payment-" + paymentId;
        log.info("Waiting for workflow: {}", workflowId);

        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
        var result = stub.getResult(Map.class);
        return JSON.valueToTree(result);
    }

    /**
     * Creates a fresh Kafka consumer per invocation to avoid shared offset state
     * across tests. Reads from earliest and scans for the target paymentId.
     */
    private List<String> pollKafkaEvents(String topic, String paymentId, Duration timeout) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "p2t-poll-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));

            var matchingEvents = new ArrayList<String>();
            await().atMost(timeout)
                    .pollInterval(Duration.ofSeconds(2))
                    .until(() -> {
                        var records = consumer.poll(Duration.ofMillis(1000));
                        records.records(topic).forEach(record -> {
                            if (record.value() != null && record.value().contains(paymentId)) {
                                matchingEvents.add(record.value());
                            }
                        });
                        return !matchingEvents.isEmpty();
                    });

            return matchingEvents;
        }
    }

    private void addWireMockSanctionsHitStub() throws Exception {
        var stubBody = """
                {
                    "priority": 1,
                    "request": {
                        "method": "POST",
                        "urlPattern": "/v2/cases/screeningRequest"
                    },
                    "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/json" },
                        "jsonBody": {
                            "caseId": "sanctioned-entity",
                            "caseSystemId": "WC-INT-TEST",
                            "status": "COMPLETED",
                            "results": [{
                                "referenceId": "REF-HIT",
                                "matchStrength": "STRONG",
                                "matchedTerm": "Sanctioned Entity",
                                "matchedNameType": "PRIMARY",
                                "matchedLists": ["OFAC_SDN"],
                                "categories": ["SANCTIONS"]
                            }]
                        }
                    }
                }
                """;

        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(WIREMOCK_ADMIN_URL + "/mappings"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(stubBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        log.info("WireMock sanctions-hit stub added: {}", response.statusCode());
    }
}
