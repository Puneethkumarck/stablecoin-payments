package com.stablecoin.payments.phase2;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 load test — 100 concurrent payment initiations.
 * <p>
 * Tagged with "load" so it's excluded from regular test runs.
 * Run with: {@code ./gradlew :phase2-integration-tests:loadTest}
 */
@Tag("load")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Phase 2 — Load Test")
class Phase2LoadTest {

    private static final Logger log = LoggerFactory.getLogger(Phase2LoadTest.class);

    private static final String S1_BASE_URL = "http://localhost:8082/orchestrator";
    private static final String TEMPORAL_ADDRESS = "localhost:7233";
    private static final int CONCURRENT_PAYMENTS = 100;

    private static final JsonMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private HttpClient httpClient;
    private WorkflowClient workflowClient;

    @BeforeAll
    void setupClients() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
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
    void teardown() {
        // WorkflowClient and HttpClient are auto-closeable via GC
    }

    @Test
    @DisplayName("should handle 100 concurrent payment initiations")
    void shouldHandle100ConcurrentPayments() throws Exception {
        var succeeded = new AtomicInteger(0);
        var failed = new AtomicInteger(0);
        var paymentIds = new ConcurrentHashMap<String, String>();
        var errors = new ConcurrentHashMap<String, String>();

        var start = Instant.now();
        log.info("Starting {} concurrent payment initiations", CONCURRENT_PAYMENTS);

        // Phase 1: Initiate all payments concurrently
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();

            for (int i = 0; i < CONCURRENT_PAYMENTS; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        var senderId = UUID.randomUUID();
                        var recipientId = UUID.randomUUID();
                        var idempotencyKey = UUID.randomUUID().toString();

                        var response = initiatePayment(senderId, recipientId, "100.00", idempotencyKey);

                        if (response.statusCode() == 201) {
                            var body = JSON.readTree(response.body());
                            var paymentId = body.get("paymentId").asText();
                            paymentIds.put("payment-" + index, paymentId);
                            succeeded.incrementAndGet();
                        } else {
                            errors.put("payment-" + index,
                                    "HTTP " + response.statusCode() + ": " + response.body());
                            failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.put("payment-" + index, e.getMessage());
                        failed.incrementAndGet();
                    }
                }));
            }

            // Wait for all initiation requests to complete
            for (var future : futures) {
                future.get();
            }
        }

        var initiationDuration = Duration.between(start, Instant.now());
        log.info("Initiation phase complete: {} succeeded, {} failed in {}ms",
                succeeded.get(), failed.get(), initiationDuration.toMillis());

        if (!errors.isEmpty()) {
            log.warn("Errors during initiation:");
            errors.forEach((key, error) -> log.warn("  {}: {}", key, error));
        }

        // Phase 2: Wait for all workflows to complete
        var workflowStart = Instant.now();
        var completed = new AtomicInteger(0);
        var workflowFailed = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();

            for (var entry : paymentIds.entrySet()) {
                futures.add(executor.submit(() -> {
                    try {
                        var workflowId = "payment-" + entry.getValue();
                        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
                        var result = stub.getResult(Map.class);
                        var status = result.get("status");
                        if ("COMPLETED".equals(status)) {
                            completed.incrementAndGet();
                        } else {
                            workflowFailed.incrementAndGet();
                            log.warn("Workflow {} ended with status: {}", workflowId, status);
                        }
                    } catch (Exception e) {
                        workflowFailed.incrementAndGet();
                        log.warn("Workflow error for {}: {}", entry.getKey(), e.getMessage());
                    }
                }));
            }

            for (var future : futures) {
                future.get();
            }
        }

        var workflowDuration = Duration.between(workflowStart, Instant.now());
        var totalDuration = Duration.between(start, Instant.now());

        log.info("=== Load Test Results ===");
        log.info("Total payments: {}", CONCURRENT_PAYMENTS);
        log.info("HTTP 201 Created: {}", succeeded.get());
        log.info("HTTP failures: {}", failed.get());
        log.info("Workflows completed: {}", completed.get());
        log.info("Workflows failed: {}", workflowFailed.get());
        log.info("Initiation time: {}ms", initiationDuration.toMillis());
        log.info("Workflow completion time: {}ms", workflowDuration.toMillis());
        log.info("Total time: {}ms", totalDuration.toMillis());
        log.info("Effective TPS: {}", succeeded.get() * 1000L / Math.max(initiationDuration.toMillis(), 1));

        // Assert: at least 90% success rate
        assertThat(succeeded.get())
                .as("At least 90%% of payments should be initiated successfully")
                .isGreaterThanOrEqualTo((int) (CONCURRENT_PAYMENTS * 0.9));

        assertThat(completed.get())
                .as("At least 90%% of initiated workflows should complete")
                .isGreaterThanOrEqualTo((int) (succeeded.get() * 0.9));
    }

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
}
