package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.port.WebhookSignatureValidator;
import com.stablecoin.payments.onramp.domain.service.WebhookCommand;
import com.stablecoin.payments.onramp.domain.service.WebhookCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.anAwaitingConfirmationOrder;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aSucceededEventJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookController")
class StripeWebhookControllerTest {

    @Mock private WebhookSignatureValidator signatureValidator;
    @Mock private WebhookCommandHandler webhookCommandHandler;

    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(signatureValidator, webhookCommandHandler);
    }

    @Nested
    @DisplayName("Signature validation")
    class SignatureValidation {

        @Test
        @DisplayName("should return 400 when Stripe-Signature header is missing")
        void returnsBadRequestWhenHeaderMissing() {
            var rawBody = aSucceededEventJson();

            var response = controller.handleWebhook(rawBody, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Missing Stripe-Signature header");
        }

        @Test
        @DisplayName("should return 400 when Stripe-Signature header is blank")
        void returnsBadRequestWhenHeaderBlank() {
            var rawBody = aSucceededEventJson();

            var response = controller.handleWebhook(rawBody, "   ");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Missing Stripe-Signature header");
        }

        @Test
        @DisplayName("should return 400 when signature validation fails")
        void returnsBadRequestWhenSignatureInvalid() {
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=invalid";

            given(signatureValidator.isValid(rawBody, signature)).willReturn(false);

            var response = controller.handleWebhook(rawBody, signature);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Invalid webhook signature");
        }
    }

    @Nested
    @DisplayName("Successful webhook processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should return 200 when webhook is processed successfully")
        void returnsOkOnSuccess() {
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";
            var order = anAwaitingConfirmationOrder();

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willReturn(order.confirmCollection(new Money(new BigDecimal("1000.00"), "USD")));

            var response = controller.handleWebhook(rawBody, signature);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("status", "received");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return 404 when collection order not found")
        void returnsNotFoundWhenOrderMissing() {
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willThrow(new CollectionOrderNotFoundException(PSP_REFERENCE));

            var response = controller.handleWebhook(rawBody, signature);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 when state transition is invalid")
        void returnsConflictOnInvalidStateTransition() {
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willThrow(new IllegalStateException("Cannot transition"));

            var response = controller.handleWebhook(rawBody, signature);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("parseStripeEvent")
    class ParseStripeEvent {

        @Test
        @DisplayName("should parse succeeded event JSON into WebhookCommand")
        void parsesSucceededEvent() {
            var rawBody = aSucceededEventJson();

            var result = controller.parseStripeEvent(rawBody);

            var expected = new WebhookCommand(
                    "evt_test_001",
                    "payment_intent.succeeded",
                    PSP_REFERENCE,
                    null,
                    new Money(new BigDecimal("1000.00"), "USD"),
                    "succeeded",
                    rawBody);

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("collectionId", "rawPayload")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should convert amount from minor units to major units")
        void convertsMinorToMajorUnits() {
            var rawBody = aSucceededEventJson();

            var result = controller.parseStripeEvent(rawBody);

            assertThat(result.amount().amount().compareTo(new BigDecimal("1000.00"))).isZero();
        }

        @Test
        @DisplayName("should uppercase currency from Stripe event")
        void uppercasesCurrency() {
            var rawBody = aSucceededEventJson();

            var result = controller.parseStripeEvent(rawBody);

            assertThat(result.amount().currency()).isEqualTo("USD");
        }
    }
}
