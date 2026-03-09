package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.AbstractIntegrationTest;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.anAwaitingConfirmationOrder;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aFailedEventJson;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aSucceededEventJson;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("StripeWebhookController IT")
class StripeWebhookControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CollectionOrderRepository collectionOrderRepository;

    @Nested
    @DisplayName("Successful webhook processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should return 200 OK for valid succeeded webhook")
        void shouldReturn200ForSucceededWebhook() throws Exception {
            // given — save an awaiting-confirmation order with matching pspReference
            var order = anAwaitingConfirmationOrder();
            collectionOrderRepository.save(order);

            var rawBody = aSucceededEventJson(PSP_REFERENCE, 100000L, "usd", order.collectionId());

            // when/then — fallback validator always accepts signature
            mockMvc.perform(post("/internal/webhooks/psp/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("received")));
        }

        @Test
        @DisplayName("should return 200 OK for valid failed webhook")
        void shouldReturn200ForFailedWebhook() throws Exception {
            // given
            var order = anAwaitingConfirmationOrder();
            collectionOrderRepository.save(order);

            var rawBody = aFailedEventJson(PSP_REFERENCE, order.collectionId());

            // when/then
            mockMvc.perform(post("/internal/webhooks/psp/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("received")));
        }
    }

    @Nested
    @DisplayName("Signature validation")
    class SignatureValidation {

        @Test
        @DisplayName("should return 400 Bad Request when Stripe-Signature header is missing")
        void shouldReturn400WhenSignatureMissing() throws Exception {
            // given
            var rawBody = aSucceededEventJson();

            // when/then
            mockMvc.perform(post("/internal/webhooks/psp/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Missing Stripe-Signature header")));
        }

        @Test
        @DisplayName("should return 400 Bad Request when Stripe-Signature header is blank")
        void shouldReturn400WhenSignatureBlank() throws Exception {
            // given
            var rawBody = aSucceededEventJson();

            // when/then
            mockMvc.perform(post("/internal/webhooks/psp/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "   ")
                            .content(rawBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Missing Stripe-Signature header")));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return 404 Not Found when collection order not found for pspReference")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            // given — no order saved matching this pspReference
            var rawBody = aSucceededEventJson("pi_unknown_123", 100000L, "usd", UUID.randomUUID());

            // when/then
            mockMvc.perform(post("/internal/webhooks/psp/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=1234567890,v1=dummy")
                            .content(rawBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", notNullValue()));
        }
    }
}
