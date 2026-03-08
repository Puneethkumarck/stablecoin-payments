package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.WEBHOOK_SECRET;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aSucceededEventJson;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.computeSignature;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StripeSignatureValidator")
class StripeSignatureValidatorTest {

    private StripeSignatureValidator validator;

    @BeforeEach
    void setUp() {
        var properties = new StripeWebhookProperties(WEBHOOK_SECRET, 300);
        validator = new StripeSignatureValidator(properties);
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should accept valid signature with matching HMAC and recent timestamp")
        void shouldAcceptValidSignature() {
            // given
            var payload = aSucceededEventJson();
            var signature = computeSignature(payload, WEBHOOK_SECRET);

            // when
            var result = validator.isValid(payload, signature);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should reject signature with wrong secret")
        void shouldRejectWrongSecret() {
            // given
            var payload = aSucceededEventJson();
            var signature = computeSignature(payload, "whsec_wrong_secret");

            // when
            var result = validator.isValid(payload, signature);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject signature with tampered payload")
        void shouldRejectTamperedPayload() {
            // given
            var originalPayload = aSucceededEventJson();
            var signature = computeSignature(originalPayload, WEBHOOK_SECRET);
            var tamperedPayload = originalPayload.replace("succeeded", "failed");

            // when
            var result = validator.isValid(tamperedPayload, signature);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject expired timestamp beyond tolerance")
        void shouldRejectExpiredTimestamp() {
            // given
            var payload = aSucceededEventJson();
            var oldTimestamp = Instant.now().minusSeconds(600);
            var signature = computeSignature(payload, WEBHOOK_SECRET, oldTimestamp);

            // when
            var result = validator.isValid(payload, signature);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject null payload")
        void shouldRejectNullPayload() {
            // when
            var result = validator.isValid(null, "t=123,v1=abc");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject null signature")
        void shouldRejectNullSignature() {
            // when
            var result = validator.isValid(aSucceededEventJson(), null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject blank signature")
        void shouldRejectBlankSignature() {
            // when
            var result = validator.isValid(aSucceededEventJson(), "   ");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject malformed signature without t= or v1=")
        void shouldRejectMalformedSignature() {
            // when
            var result = validator.isValid(aSucceededEventJson(), "invalid_format");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject signature with invalid timestamp format")
        void shouldRejectInvalidTimestampFormat() {
            // when
            var result = validator.isValid(aSucceededEventJson(), "t=not_a_number,v1=abc123");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should accept timestamp within tolerance window")
        void shouldAcceptTimestampWithinTolerance() {
            // given
            var payload = aSucceededEventJson();
            var recentTimestamp = Instant.now().minusSeconds(60);
            var signature = computeSignature(payload, WEBHOOK_SECRET, recentTimestamp);

            // when
            var result = validator.isValid(payload, signature);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("parseSignature")
    class ParseSignature {

        @Test
        @DisplayName("should parse valid Stripe signature header")
        void shouldParseValidHeader() {
            // when
            var parsed = validator.parseSignature("t=1614556828,v1=abc123def456");

            // then
            var expected = new StripeSignatureValidator.ParsedSignature("1614556828", "abc123def456");
            assertThat(parsed).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should return null for missing t= component")
        void shouldReturnNullForMissingTimestamp() {
            // when
            var parsed = validator.parseSignature("v1=abc123");

            // then
            assertThat(parsed).isNull();
        }

        @Test
        @DisplayName("should return null for missing v1= component")
        void shouldReturnNullForMissingV1() {
            // when
            var parsed = validator.parseSignature("t=1614556828");

            // then
            assertThat(parsed).isNull();
        }

        @Test
        @DisplayName("should handle extra unknown components")
        void shouldHandleExtraComponents() {
            // when
            var parsed = validator.parseSignature("t=1614556828,v1=abc123,v0=old_sig");

            // then
            var expected = new StripeSignatureValidator.ParsedSignature("1614556828", "abc123");
            assertThat(parsed).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("computeHmac")
    class ComputeHmac {

        @Test
        @DisplayName("should produce deterministic HMAC for same inputs")
        void shouldProduceDeterministicHmac() {
            // when
            var hmac1 = validator.computeHmac("12345", "test_payload");
            var hmac2 = validator.computeHmac("12345", "test_payload");

            // then
            assertThat(hmac1).isEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different payloads")
        void shouldProduceDifferentHmacForDifferentPayloads() {
            // when
            var hmac1 = validator.computeHmac("12345", "payload_a");
            var hmac2 = validator.computeHmac("12345", "payload_b");

            // then
            assertThat(hmac1).isNotEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different timestamps")
        void shouldProduceDifferentHmacForDifferentTimestamps() {
            // when
            var hmac1 = validator.computeHmac("12345", "test_payload");
            var hmac2 = validator.computeHmac("67890", "test_payload");

            // then
            assertThat(hmac1).isNotEqualTo(hmac2);
        }
    }
}
