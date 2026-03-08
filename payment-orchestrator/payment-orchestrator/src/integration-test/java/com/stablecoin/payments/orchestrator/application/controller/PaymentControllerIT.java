package com.stablecoin.payments.orchestrator.application.controller;

import com.stablecoin.payments.orchestrator.AbstractIntegrationTest;
import com.stablecoin.payments.orchestrator.domain.port.PaymentRepository;
import com.stablecoin.payments.orchestrator.domain.workflow.PaymentWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_AMOUNT_VALUE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aCompletedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aFailedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiatedPayment;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PaymentController IT")
class PaymentControllerIT extends AbstractIntegrationTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private WorkflowClient workflowClient;

    @MockitoBean
    private WorkflowServiceStubs workflowServiceStubs;

    @MockitoBean
    private WorkerFactory workerFactory;

    @BeforeEach
    void setupTemporalMock() {
        var workflowStub = mock(PaymentWorkflow.class);
        given(workflowClient.newWorkflowStub(eq(PaymentWorkflow.class), any(WorkflowOptions.class)))
                .willReturn(workflowStub);
        given(workflowClient.newWorkflowStub(eq(PaymentWorkflow.class), anyString()))
                .willReturn(workflowStub);
    }

    @Nested
    @DisplayName("POST /v1/payments")
    class InitiatePayment {

        @Test
        @DisplayName("should return 201 Created for new payment")
        void shouldReturn201ForNewPayment() throws Exception {
            var idempotencyKey = UUID.randomUUID().toString();

            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(validInitiateRequestBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentId", notNullValue()))
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andExpect(jsonPath("$.senderId", is(SENDER_ID.toString())))
                    .andExpect(jsonPath("$.recipientId", is(RECIPIENT_ID.toString())))
                    .andExpect(jsonPath("$.sourceAmount").value(SOURCE_AMOUNT_VALUE.doubleValue()))
                    .andExpect(jsonPath("$.sourceCurrency", is(SOURCE_CURRENCY)))
                    .andExpect(jsonPath("$.targetCurrency", is(TARGET_CURRENCY)))
                    .andExpect(jsonPath("$.sourceCountry", is(SOURCE_COUNTRY)))
                    .andExpect(jsonPath("$.targetCountry", is(TARGET_COUNTRY)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay with same Idempotency-Key")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            var idempotencyKey = UUID.randomUUID().toString();

            // First request — 201 Created
            var firstResult = mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(validInitiateRequestBody()))
                    .andExpect(status().isCreated())
                    .andReturn();

            var firstPaymentId = com.jayway.jsonpath.JsonPath
                    .read(firstResult.getResponse().getContentAsString(), "$.paymentId");

            // Second request with same Idempotency-Key — 200 OK, same paymentId
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .content(validInitiateRequestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(firstPaymentId)))
                    .andExpect(jsonPath("$.state", is("INITIATED")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "sourceAmount": 100.00
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("PO-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for negative amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "senderId": "%s",
                                      "recipientId": "%s",
                                      "sourceAmount": -1,
                                      "sourceCurrency": "USD",
                                      "targetCurrency": "EUR",
                                      "sourceCountry": "US",
                                      "targetCountry": "DE"
                                    }
                                    """.formatted(SENDER_ID, RECIPIENT_ID)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("PO-0001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400ForMissingIdempotencyKey() throws Exception {
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validInitiateRequestBody()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("PO-0001")))
                    .andExpect(jsonPath("$.message",
                            is("Idempotency-Key header is required for mutating requests")));
        }
    }

    @Nested
    @DisplayName("GET /v1/payments/{paymentId}")
    class GetPayment {

        @Test
        @DisplayName("should return 200 OK with payment details")
        void shouldReturn200ForExistingPayment() throws Exception {
            var payment = anInitiatedPayment();
            paymentRepository.save(payment);

            mockMvc.perform(get("/v1/payments/{paymentId}", payment.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(payment.paymentId().toString())))
                    .andExpect(jsonPath("$.state", is("INITIATED")))
                    .andExpect(jsonPath("$.senderId", is(payment.senderId().toString())))
                    .andExpect(jsonPath("$.recipientId", is(payment.recipientId().toString())))
                    .andExpect(jsonPath("$.sourceCurrency", is(SOURCE_CURRENCY)))
                    .andExpect(jsonPath("$.targetCurrency", is(TARGET_CURRENCY)))
                    .andExpect(jsonPath("$.sourceCountry", is(SOURCE_COUNTRY)))
                    .andExpect(jsonPath("$.targetCountry", is(TARGET_COUNTRY)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing payment")
        void shouldReturn404ForNonExistingPayment() throws Exception {
            var paymentId = UUID.randomUUID();

            mockMvc.perform(get("/v1/payments/{paymentId}", paymentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("PO-2001")))
                    .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            mockMvc.perform(get("/v1/payments/{paymentId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{paymentId}/cancel")
    class CancelPayment {

        @Test
        @DisplayName("should return 200 OK when cancel accepted")
        void shouldReturn200WhenCancelAccepted() throws Exception {
            var payment = anInitiatedPayment();
            paymentRepository.save(payment);

            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", payment.paymentId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "reason": "Customer requested"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(payment.paymentId().toString())))
                    .andExpect(jsonPath("$.state", is("INITIATED")));
        }

        @Test
        @DisplayName("should return 409 Conflict for completed payment")
        void shouldReturn409ForCompletedPayment() throws Exception {
            var payment = aCompletedPayment();
            paymentRepository.save(payment);

            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", payment.paymentId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "reason": "Too late"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("PO-2002")))
                    .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        @DisplayName("should return 409 Conflict for failed payment")
        void shouldReturn409ForFailedPayment() throws Exception {
            var payment = aFailedPayment();
            paymentRepository.save(payment);

            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", payment.paymentId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "reason": "Already failed"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("PO-2002")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing payment")
        void shouldReturn404ForNonExistingPayment() throws Exception {
            var paymentId = UUID.randomUUID();

            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", paymentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("""
                                    {
                                      "reason": "Cancel please"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("PO-2001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing reason")
        void shouldReturn400ForMissingReason() throws Exception {
            var payment = anInitiatedPayment();
            paymentRepository.save(payment);

            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", payment.paymentId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("PO-0001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400ForMissingIdempotencyKey() throws Exception {
            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "reason": "Cancel please"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("PO-0001")))
                    .andExpect(jsonPath("$.message",
                            is("Idempotency-Key header is required for mutating requests")));
        }
    }

    private static String validInitiateRequestBody() {
        return """
                {
                  "senderId": "%s",
                  "recipientId": "%s",
                  "sourceAmount": %s,
                  "sourceCurrency": "%s",
                  "targetCurrency": "%s",
                  "sourceCountry": "%s",
                  "targetCountry": "%s"
                }
                """.formatted(SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT_VALUE,
                SOURCE_CURRENCY, TARGET_CURRENCY,
                SOURCE_COUNTRY, TARGET_COUNTRY);
    }
}
