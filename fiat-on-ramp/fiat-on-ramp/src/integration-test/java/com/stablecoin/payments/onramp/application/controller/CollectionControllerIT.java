package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.AbstractIntegrationTest;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.CORRELATION_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectionRequest;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aRefundRequest;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CollectionController IT")
class CollectionControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CollectionOrderRepository collectionOrderRepository;

    @Nested
    @DisplayName("POST /v1/collections")
    class InitiateCollection {

        @Test
        @DisplayName("should return 201 Created for new collection order")
        void shouldReturn201ForNewCollection() throws Exception {
            // given
            var request = aCollectionRequest();

            // when/then
            mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.collectionId", notNullValue()))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("AWAITING_CONFIRMATION")))
                    .andExpect(jsonPath("$.paymentRail", is("SEPA")))
                    .andExpect(jsonPath("$.psp", is("Stripe")))
                    .andExpect(jsonPath("$.pspReference", notNullValue()))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay with same paymentId")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            // given
            var request = aCollectionRequest();

            // first request — 201 Created
            mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // when — second request with same paymentId
            mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            // given/when/then
            mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("OR-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for negative amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            // given/when/then
            mockMvc.perform(post("/v1/collections")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "amount": -1,
                                      "currency": "USD",
                                      "paymentRailType": "SEPA",
                                      "railCountry": "DE",
                                      "railCurrency": "EUR",
                                      "pspId": "stripe_001",
                                      "pspName": "Stripe",
                                      "senderAccountHash": "sha256_abc",
                                      "senderBankCode": "DEUTDEFF",
                                      "senderAccountType": "IBAN",
                                      "senderAccountCountry": "DE"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("OR-0001")));
        }
    }

    @Nested
    @DisplayName("GET /v1/collections/{collectionId}")
    class GetCollectionById {

        @Test
        @DisplayName("should return 200 OK with collection details")
        void shouldReturn200ForExistingCollection() throws Exception {
            // given
            var order = aPendingOrder();
            var saved = collectionOrderRepository.save(order);

            // when/then
            mockMvc.perform(get("/v1/collections/{collectionId}", saved.collectionId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.collectionId", is(saved.collectionId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing collection")
        void shouldReturn404ForNonExistingCollection() throws Exception {
            // given
            var collectionId = UUID.randomUUID();

            // when/then
            mockMvc.perform(get("/v1/collections/{collectionId}", collectionId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("OR-1001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            // when/then
            mockMvc.perform(get("/v1/collections/{collectionId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/collections?paymentId=")
    class GetCollectionByPaymentId {

        @Test
        @DisplayName("should return 200 OK when found by paymentId")
        void shouldReturn200ForExistingPaymentId() throws Exception {
            // given
            var order = aPendingOrder();
            collectionOrderRepository.save(order);

            // when/then
            mockMvc.perform(get("/v1/collections")
                            .param("paymentId", PAYMENT_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing paymentId")
        void shouldReturn404ForNonExistingPaymentId() throws Exception {
            // given
            var paymentId = UUID.randomUUID();

            // when/then
            mockMvc.perform(get("/v1/collections")
                            .param("paymentId", paymentId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("OR-1001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid paymentId format")
        void shouldReturn400ForInvalidPaymentId() throws Exception {
            // when/then
            mockMvc.perform(get("/v1/collections")
                            .param("paymentId", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/collections/{collectionId}/refunds")
    class InitiateRefund {

        @Test
        @DisplayName("should return 201 Created for valid refund on collected order")
        void shouldReturn201ForValidRefund() throws Exception {
            // given
            var order = aCollectedOrder();
            var saved = collectionOrderRepository.save(order);
            var request = aRefundRequest();

            // when/then
            mockMvc.perform(post("/v1/collections/{collectionId}/refunds", saved.collectionId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.refundId", notNullValue()))
                    .andExpect(jsonPath("$.collectionId", is(saved.collectionId().toString())))
                    .andExpect(jsonPath("$.status", is("COMPLETED")));
        }

        @Test
        @DisplayName("should return 409 Conflict when collection is not in COLLECTED state")
        void shouldReturn409WhenNotCollected() throws Exception {
            // given
            var order = aPendingOrder();
            var saved = collectionOrderRepository.save(order);
            var request = aRefundRequest();

            // when/then
            mockMvc.perform(post("/v1/collections/{collectionId}/refunds", saved.collectionId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("OR-2002")));
        }

        @Test
        @DisplayName("should return 422 Unprocessable Entity when refund amount exceeds collected")
        void shouldReturn422WhenAmountExceedsCollected() throws Exception {
            // given
            var order = aCollectedOrder();
            var saved = collectionOrderRepository.save(order);

            // when/then
            mockMvc.perform(post("/v1/collections/{collectionId}/refunds", saved.collectionId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "refundAmount": 9999.00,
                                      "currency": "USD",
                                      "reason": "Excessive refund"
                                    }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code", is("OR-2003")));
        }

        @Test
        @DisplayName("should return 404 Not Found when collection does not exist")
        void shouldReturn404WhenCollectionNotFound() throws Exception {
            // given
            var collectionId = UUID.randomUUID();
            var request = aRefundRequest();

            // when/then
            mockMvc.perform(post("/v1/collections/{collectionId}/refunds", collectionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("OR-1001")));
        }
    }
}
