package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.port.WebhookSignatureValidator;
import com.stablecoin.payments.offramp.domain.service.WebhookCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutProcessingOrder;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.PARTNER_NAME;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.nextEventId;
import static com.stablecoin.payments.offramp.fixtures.WebhookFixtures.settlementPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerWebhookController")
class PartnerWebhookControllerTest {

    @Mock
    private WebhookSignatureValidator signatureValidator;

    @Mock
    private WebhookCommandHandler webhookCommandHandler;

    @InjectMocks
    private PartnerWebhookController controller;

    private static final String VALID_SIGNATURE = "t=1234567890,v1=abc123";
    private static final String RAW_BODY = settlementPayload(nextEventId());

    @Nested
    @DisplayName("Signature validation")
    class SignatureValidation {

        @Test
        @DisplayName("should return 400 when signature header is missing")
        void shouldReturn400WhenSignatureMissing() {
            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Missing X-Webhook-Signature header");
        }

        @Test
        @DisplayName("should return 400 when signature header is blank")
        void shouldReturn400WhenSignatureBlank() {
            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, "");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when signature is invalid")
        void shouldReturn400WhenSignatureInvalid() {
            given(signatureValidator.isValid(RAW_BODY, VALID_SIGNATURE)).willReturn(false);

            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Invalid webhook signature");
        }
    }

    @Nested
    @DisplayName("Webhook processing")
    class WebhookProcessing {

        @Test
        @DisplayName("should return 200 on successful settlement webhook")
        void shouldReturn200OnSuccess() {
            given(signatureValidator.isValid(RAW_BODY, VALID_SIGNATURE)).willReturn(true);
            var order = aPayoutProcessingOrder();
            given(webhookCommandHandler.handleWebhook(
                    controller.parsePartnerEvent(PARTNER_NAME, RAW_BODY)))
                    .willReturn(order);

            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("status", "received");
        }

        @Test
        @DisplayName("should return 404 when payout order not found")
        void shouldReturn404WhenNotFound() {
            given(signatureValidator.isValid(RAW_BODY, VALID_SIGNATURE)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    controller.parsePartnerEvent(PARTNER_NAME, RAW_BODY)))
                    .willThrow(new PayoutNotFoundException("modulr_ref_12345"));

            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 409 on invalid state transition")
        void shouldReturn409OnStateConflict() {
            given(signatureValidator.isValid(RAW_BODY, VALID_SIGNATURE)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    controller.parsePartnerEvent(PARTNER_NAME, RAW_BODY)))
                    .willThrow(new IllegalStateException("Invalid transition"));

            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 400 on malformed webhook payload")
        void shouldReturn400OnMalformedPayload() {
            var malformedBody = "{\"event_id\":\"evt1\",\"settled_at\":\"not-a-timestamp\"}";
            given(signatureValidator.isValid(malformedBody, VALID_SIGNATURE)).willReturn(true);

            var response = controller.handleWebhook(PARTNER_NAME, malformedBody, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsKey("error");
        }

        @Test
        @DisplayName("should return 500 on unexpected error")
        void shouldReturn500OnUnexpectedError() {
            given(signatureValidator.isValid(RAW_BODY, VALID_SIGNATURE)).willReturn(true);
            given(webhookCommandHandler.handleWebhook(
                    controller.parsePartnerEvent(PARTNER_NAME, RAW_BODY)))
                    .willThrow(new RuntimeException("Unexpected"));

            var response = controller.handleWebhook(PARTNER_NAME, RAW_BODY, VALID_SIGNATURE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("JSON parsing")
    class JsonParsing {

        @Test
        @DisplayName("should parse settlement webhook payload correctly")
        void shouldParseSettlementPayload() {
            var command = controller.parsePartnerEvent(PARTNER_NAME, RAW_BODY);

            assertThat(command.eventType()).isEqualTo("payment.settled");
            assertThat(command.partnerName()).isEqualTo(PARTNER_NAME);
            assertThat(command.status()).isEqualTo("SETTLED");
            assertThat(command.partnerReference()).isNotBlank();
        }

        @Test
        @DisplayName("should throw on invalid JSON")
        void shouldThrowOnInvalidJson() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> controller.parsePartnerEvent(PARTNER_NAME, "not-json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to parse");
        }
    }
}
