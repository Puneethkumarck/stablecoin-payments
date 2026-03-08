package com.stablecoin.payments.compliance.application.controller;

import com.stablecoin.payments.compliance.AbstractIntegrationTest;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.compliance.application.filter.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ComplianceCheckController IT")
class ComplianceCheckControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplianceCheckRepository checkRepository;

    @Nested
    @DisplayName("POST /v1/compliance/check")
    class InitiateCheck {

        @Test
        @DisplayName("should return 202 Accepted with check response")
        void shouldReturn202WithCheckResponse() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

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

            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.checkId", notNullValue()))
                    .andExpect(jsonPath("$.status", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            var requestBody = """
                    {
                        "amount": 1000.00,
                        "currency": "USD"
                    }
                    """;

            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("CO-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid currency code")
        void shouldReturn400ForInvalidCurrency() throws Exception {
            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": 1000.00,
                        "currency": "USDX",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("CO-0001")));
        }

        @Test
        @DisplayName("should return 409 Conflict for duplicate payment")
        void shouldReturn409ForDuplicatePayment() throws Exception {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();

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

            // First request should succeed
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isAccepted());

            // Second request with same paymentId should return 409
            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("CO-1002")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for negative amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            var requestBody = """
                    {
                        "paymentId": "%s",
                        "senderId": "%s",
                        "recipientId": "%s",
                        "amount": -100.00,
                        "currency": "USD",
                        "sourceCountry": "US",
                        "targetCountry": "DE",
                        "targetCurrency": "EUR"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("CO-0001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400ForMissingIdempotencyKey() throws Exception {
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
                    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/compliance/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("CO-0001")))
                    .andExpect(jsonPath("$.message", is("Idempotency-Key header is required for mutating requests")));
        }
    }

    @Nested
    @DisplayName("GET /v1/compliance/checks/{checkId}")
    class GetCheck {

        @Test
        @DisplayName("should return 200 OK with check response for existing check")
        void shouldReturn200ForExistingCheck() throws Exception {
            var check = ComplianceCheck.initiate(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new Money(new BigDecimal("1000.00"), "USD"), "US", "DE", "EUR");
            var saved = checkRepository.save(check);

            mockMvc.perform(get("/v1/compliance/checks/{checkId}", saved.checkId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkId", is(saved.checkId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(saved.paymentId().toString())))
                    .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing check")
        void shouldReturn404ForNonExistingCheck() throws Exception {
            var checkId = UUID.randomUUID();

            mockMvc.perform(get("/v1/compliance/checks/{checkId}", checkId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("CO-1001")));
        }
    }
}
