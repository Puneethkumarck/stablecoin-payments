package com.stablecoin.payments.orchestrator.application.controller;

import com.stablecoin.payments.orchestrator.application.security.SecurityConfig;
import com.stablecoin.payments.orchestrator.domain.model.PaymentNotCancellableException;
import com.stablecoin.payments.orchestrator.domain.model.PaymentNotFoundException;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.domain.service.PaymentCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_AMOUNT_VALUE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anIdempotentReplayResult;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiateResult;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiatedPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.enabled=false")
@DisplayName("PaymentController MVC")
class PaymentControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCommandHandler commandHandler;

    @Nested
    @DisplayName("POST /v1/payments")
    class InitiatePayment {

        @Test
        @DisplayName("should return 201 Created for new payment")
        void shouldReturn201() throws Exception {
            // given
            var result = anInitiateResult();
            given(commandHandler.initiatePayment(
                    eq(IDEMPOTENCY_KEY), any(UUID.class),
                    eq(SENDER_ID), eq(RECIPIENT_ID), eq(SOURCE_AMOUNT_VALUE),
                    eq(SOURCE_CURRENCY), eq(TARGET_CURRENCY),
                    eq(SOURCE_COUNTRY), eq(TARGET_COUNTRY)))
                    .willReturn(result);

            // when/then
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content("""
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
                                    SOURCE_COUNTRY, TARGET_COUNTRY)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentId").exists())
                    .andExpect(jsonPath("$.state").value("INITIATED"));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay")
        void shouldReturn200ForReplay() throws Exception {
            // given
            var result = anIdempotentReplayResult();
            given(commandHandler.initiatePayment(
                    eq(IDEMPOTENCY_KEY), any(UUID.class),
                    eq(SENDER_ID), eq(RECIPIENT_ID), eq(SOURCE_AMOUNT_VALUE),
                    eq(SOURCE_CURRENCY), eq(TARGET_CURRENCY),
                    eq(SOURCE_COUNTRY), eq(TARGET_COUNTRY)))
                    .willReturn(result);

            // when/then
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content("""
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
                                    SOURCE_COUNTRY, TARGET_COUNTRY)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").exists())
                    .andExpect(jsonPath("$.state").value("INITIATED"));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid request body")
        void shouldReturn400ForInvalidBody() throws Exception {
            // when/then
            mockMvc.perform(post("/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content("""
                                    {
                                      "sourceAmount": -1
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PO-0001"));
        }
    }

    @Nested
    @DisplayName("GET /v1/payments/{paymentId}")
    class GetPayment {

        @Test
        @DisplayName("should return 200 OK with payment details")
        void shouldReturn200() throws Exception {
            // given
            var payment = anInitiatedPayment();
            given(commandHandler.getPayment(payment.paymentId()))
                    .willReturn(payment);

            // when/then
            mockMvc.perform(get("/v1/payments/{paymentId}", payment.paymentId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value(payment.paymentId().toString()))
                    .andExpect(jsonPath("$.state").value("INITIATED"))
                    .andExpect(jsonPath("$.senderId").value(payment.senderId().toString()));
        }

        @Test
        @DisplayName("should return 404 Not Found when payment does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            // given
            var paymentId = UUID.randomUUID();
            given(commandHandler.getPayment(paymentId))
                    .willThrow(new PaymentNotFoundException(paymentId));

            // when/then
            mockMvc.perform(get("/v1/payments/{paymentId}", paymentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PO-2001"));
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{paymentId}/cancel")
    class CancelPayment {

        @Test
        @DisplayName("should return 200 OK when cancel accepted")
        void shouldReturn200() throws Exception {
            // given
            var payment = anInitiatedPayment();
            given(commandHandler.cancelPayment(payment.paymentId(), "Customer requested"))
                    .willReturn(payment);

            // when/then
            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", payment.paymentId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "cancel-key-1")
                            .content("""
                                    {
                                      "reason": "Customer requested"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value(payment.paymentId().toString()))
                    .andExpect(jsonPath("$.state").value("INITIATED"));
        }

        @Test
        @DisplayName("should return 409 Conflict for terminal payment")
        void shouldReturn409WhenTerminal() throws Exception {
            // given
            var paymentId = UUID.randomUUID();
            given(commandHandler.cancelPayment(paymentId, "reason"))
                    .willThrow(new PaymentNotCancellableException(paymentId, PaymentState.COMPLETED));

            // when/then
            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", paymentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "cancel-key-2")
                            .content("""
                                    {
                                      "reason": "reason"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PO-2002"));
        }

        @Test
        @DisplayName("should return 404 Not Found when payment does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            // given
            var paymentId = UUID.randomUUID();
            given(commandHandler.cancelPayment(paymentId, "reason"))
                    .willThrow(new PaymentNotFoundException(paymentId));

            // when/then
            mockMvc.perform(post("/v1/payments/{paymentId}/cancel", paymentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "cancel-key-3")
                            .content("""
                                    {
                                      "reason": "reason"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PO-2001"));
        }
    }
}
