package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.AbstractIntegrationTest;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutInitiatedOrder;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.PARTNER_NAME;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.failurePayload;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.nextEventId;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.settlementPayload;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PartnerWebhookController IT")
class PartnerWebhookControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayoutOrderRepository payoutOrderRepository;

    @Nested
    @DisplayName("Successful webhook processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should return 200 OK for valid settlement webhook")
        void shouldReturn200ForSettlementWebhook() throws Exception {
            // given — save an order in PAYOUT_INITIATED state with matching partnerReference
            var order = aPayoutInitiatedOrder();
            payoutOrderRepository.save(order);

            var rawBody = settlementPayload(nextEventId());

            // when/then — fallback validator always accepts signature
            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("received")));
        }

        @Test
        @DisplayName("should return 200 OK for valid failure webhook")
        void shouldReturn200ForFailureWebhook() throws Exception {
            // given
            var order = aPayoutInitiatedOrder();
            payoutOrderRepository.save(order);

            var rawBody = failurePayload(nextEventId());

            // when/then
            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("received")));
        }

        @Test
        @DisplayName("should return 200 OK idempotently for already-completed payout")
        void shouldReturn200ForAlreadyCompletedPayout() throws Exception {
            // given — save and complete the order via first webhook
            var order = aPayoutInitiatedOrder();
            payoutOrderRepository.save(order);

            var firstPayload = settlementPayload(nextEventId());
            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content(firstPayload))
                    .andExpect(status().isOk());

            // when — second settlement webhook for same order
            var secondPayload = settlementPayload(nextEventId());
            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content(secondPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("received")));
        }
    }

    @Nested
    @DisplayName("Signature validation")
    class SignatureValidation {

        @Test
        @DisplayName("should return 400 Bad Request when X-Webhook-Signature header is missing")
        void shouldReturn400WhenSignatureMissing() throws Exception {
            var rawBody = settlementPayload(nextEventId());

            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Missing X-Webhook-Signature header")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when X-Webhook-Signature header is blank")
        void shouldReturn400WhenSignatureBlank() throws Exception {
            var rawBody = settlementPayload(nextEventId());

            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "   ")
                            .content(rawBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Missing X-Webhook-Signature header")));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return 404 Not Found when payout order not found for partnerReference")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            // given — no order saved matching PARTNER_REFERENCE
            var rawBody = settlementPayload(nextEventId());

            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for malformed JSON payload")
        void shouldReturn400ForMalformedJson() throws Exception {
            mockMvc.perform(post("/internal/webhooks/partner/{partnerName}", PARTNER_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Webhook-Signature", "t=1234567890,v1=dummy")
                            .content("not-valid-json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", notNullValue()));
        }
    }
}
