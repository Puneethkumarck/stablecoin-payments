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
        void shouldReturnBadRequestWhenHeaderMissing() {
            // given
            var rawBody = aSucceededEventJson();

            // when
            var response = controller.handleWebhook(rawBody, null);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when Stripe-Signature header is blank")
        void shouldReturnBadRequestWhenHeaderBlank() {
            // given
            var rawBody = aSucceededEventJson();

            // when
            var response = controller.handleWebhook(rawBody, "   ");

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when signature validation fails")
        void shouldReturnBadRequestWhenSignatureInvalid() {
            // given
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=invalid";
            given(signatureValidator.isValid(rawBody, signature)).willReturn(false);

            // when
            var response = controller.handleWebhook(rawBody, signature);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Successful webhook processing")
    class SuccessfulProcessing {

        @Test
        @DisplayName("should return 200 when webhook is processed successfully")
        void shouldReturnOkOnSuccess() {
            // given
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";
            var order = anAwaitingConfirmationOrder();

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willReturn(order.confirmCollection(new Money(new BigDecimal("1000.00"), "USD")));

            // when
            var response = controller.handleWebhook(rawBody, signature);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return 404 when collection order not found")
        void shouldReturnNotFoundWhenOrderMissing() {
            // given
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willThrow(new CollectionOrderNotFoundException(PSP_REFERENCE));

            // when
            var response = controller.handleWebhook(rawBody, signature);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 when state transition is invalid")
        void shouldReturnConflictOnInvalidStateTransition() {
            // given
            var rawBody = aSucceededEventJson();
            var signature = "t=123,v1=valid";

            given(signatureValidator.isValid(rawBody, signature)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    eqIgnoringTimestamps(controller.parseStripeEvent(rawBody))))
                    .willThrow(new IllegalStateException("Cannot transition"));

            // when
            var response = controller.handleWebhook(rawBody, signature);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("parseStripeEvent")
    class ParseStripeEvent {

        @Test
        @DisplayName("should parse succeeded event JSON into WebhookCommand")
        void shouldParseSucceededEvent() {
            // given
            var rawBody = aSucceededEventJson();

            // when
            var result = controller.parseStripeEvent(rawBody);

            // then
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
    }
}
