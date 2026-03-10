package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.AbstractIntegrationTest;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.stablecoin.payments.offramp.application.filter.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.CORRELATION_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aHoldPayoutRequest;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutInitiatedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutRequest;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PayoutController IT")
class PayoutControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PayoutOrderRepository payoutOrderRepository;

    @Nested
    @DisplayName("POST /v1/payouts")
    class InitiatePayout {

        @Test
        @DisplayName("should return 202 Accepted for new FIAT payout")
        void shouldReturn202ForNewFiatPayout() throws Exception {
            var request = aPayoutRequest();

            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.payoutId", notNullValue()))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("PAYOUT_INITIATED")))
                    .andExpect(jsonPath("$.payoutType", is("FIAT")))
                    .andExpect(jsonPath("$.paymentRail", is("SEPA")))
                    .andExpect(jsonPath("$.partner", is("Modulr")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        @DisplayName("should return 202 Accepted for HOLD_STABLECOIN payout")
        void shouldReturn202ForHoldStablecoinPayout() throws Exception {
            var request = aHoldPayoutRequest();

            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.payoutId", notNullValue()))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.payoutType", is("HOLD_STABLECOIN")));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay with same paymentId")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            var request = aPayoutRequest();
            var idempotencyKey = UUID.randomUUID().toString();

            // first request — 202 Accepted
            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());

            // second request with same paymentId — 200 OK
            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("OF-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for negative redeemed amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferId": "%s",
                                      "payoutType": "FIAT",
                                      "stablecoin": "USDC",
                                      "redeemedAmount": -100,
                                      "targetCurrency": "EUR",
                                      "appliedFxRate": 0.92,
                                      "recipientId": "%s",
                                      "recipientAccountHash": "sha256_abc",
                                      "paymentRail": "SEPA",
                                      "offRampPartnerId": "modulr_001",
                                      "offRampPartnerName": "Modulr",
                                      "bankAccountNumber": "DE89370400440532013000",
                                      "bankCode": "DEUTDEFF",
                                      "bankAccountType": "IBAN",
                                      "bankAccountCountry": "DE"
                                    }
                                    """.formatted(UUID.randomUUID(), CORRELATION_ID,
                                    UUID.randomUUID(), UUID.randomUUID())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("OF-0001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid payout type")
        void shouldReturn400ForInvalidPayoutType() throws Exception {
            mockMvc.perform(post("/v1/payouts")
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferId": "%s",
                                      "payoutType": "INVALID_TYPE",
                                      "stablecoin": "USDC",
                                      "redeemedAmount": 1000.00,
                                      "targetCurrency": "EUR",
                                      "appliedFxRate": 0.92,
                                      "recipientId": "%s",
                                      "recipientAccountHash": "sha256_abc",
                                      "paymentRail": "SEPA",
                                      "offRampPartnerId": "modulr_001",
                                      "offRampPartnerName": "Modulr",
                                      "bankAccountNumber": "DE89370400440532013000",
                                      "bankCode": "DEUTDEFF",
                                      "bankAccountType": "IBAN",
                                      "bankAccountCountry": "DE"
                                    }
                                    """.formatted(UUID.randomUUID(), CORRELATION_ID,
                                    UUID.randomUUID(), UUID.randomUUID())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
            var request = aPayoutRequest();

            mockMvc.perform(post("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("OF-0001")))
                    .andExpect(jsonPath("$.message", is("Idempotency-Key header is required for mutating requests")));
        }
    }

    @Nested
    @DisplayName("GET /v1/payouts/{payoutId}")
    class GetPayoutById {

        @Test
        @DisplayName("should return 200 OK with payout details")
        void shouldReturn200ForExistingPayout() throws Exception {
            var order = aPayoutInitiatedOrder();
            var saved = payoutOrderRepository.save(order);

            mockMvc.perform(get("/v1/payouts/{payoutId}", saved.payoutId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payoutId", is(saved.payoutId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("PAYOUT_INITIATED")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing payout")
        void shouldReturn404ForNonExistingPayout() throws Exception {
            var payoutId = UUID.randomUUID();

            mockMvc.perform(get("/v1/payouts/{payoutId}", payoutId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("FR-1003")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            mockMvc.perform(get("/v1/payouts/{payoutId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/payouts?paymentId=")
    class GetPayoutByPaymentId {

        @Test
        @DisplayName("should return 200 OK when found by paymentId")
        void shouldReturn200ForExistingPaymentId() throws Exception {
            var order = aPayoutInitiatedOrder();
            payoutOrderRepository.save(order);

            mockMvc.perform(get("/v1/payouts")
                            .param("paymentId", PAYMENT_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("PAYOUT_INITIATED")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing paymentId")
        void shouldReturn404ForNonExistingPaymentId() throws Exception {
            var paymentId = UUID.randomUUID();

            mockMvc.perform(get("/v1/payouts")
                            .param("paymentId", paymentId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("FR-1003")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid paymentId format")
        void shouldReturn400ForInvalidPaymentId() throws Exception {
            mockMvc.perform(get("/v1/payouts")
                            .param("paymentId", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }
}
