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
        void acceptsValidSignature() {
            var payload = aSucceededEventJson();
            var signature = computeSignature(payload, WEBHOOK_SECRET);

            var result = validator.isValid(payload, signature);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should reject signature with wrong secret")
        void rejectsWrongSecret() {
            var payload = aSucceededEventJson();
            var signature = computeSignature(payload, "whsec_wrong_secret");

            var result = validator.isValid(payload, signature);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject signature with tampered payload")
        void rejectsTamperedPayload() {
            var originalPayload = aSucceededEventJson();
            var signature = computeSignature(originalPayload, WEBHOOK_SECRET);
            var tamperedPayload = originalPayload.replace("succeeded", "failed");

            var result = validator.isValid(tamperedPayload, signature);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject expired timestamp beyond tolerance")
        void rejectsExpiredTimestamp() {
            var payload = aSucceededEventJson();
            var oldTimestamp = Instant.now().minusSeconds(600);
            var signature = computeSignature(payload, WEBHOOK_SECRET, oldTimestamp);

            var result = validator.isValid(payload, signature);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject null payload")
        void rejectsNullPayload() {
            var result = validator.isValid(null, "t=123,v1=abc");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject null signature")
        void rejectsNullSignature() {
            var result = validator.isValid(aSucceededEventJson(), null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject blank signature")
        void rejectsBlankSignature() {
            var result = validator.isValid(aSucceededEventJson(), "   ");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject malformed signature without t= or v1=")
        void rejectsMalformedSignature() {
            var result = validator.isValid(aSucceededEventJson(), "invalid_format");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject signature with invalid timestamp format")
        void rejectsInvalidTimestampFormat() {
            var result = validator.isValid(aSucceededEventJson(), "t=not_a_number,v1=abc123");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should accept timestamp within tolerance window")
        void acceptsTimestampWithinTolerance() {
            var payload = aSucceededEventJson();
            var recentTimestamp = Instant.now().minusSeconds(60);
            var signature = computeSignature(payload, WEBHOOK_SECRET, recentTimestamp);

            var result = validator.isValid(payload, signature);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("parseSignature")
    class ParseSignature {

        @Test
        @DisplayName("should parse valid Stripe signature header")
        void parsesValidHeader() {
            var parsed = validator.parseSignature("t=1614556828,v1=abc123def456");

            var expected = new StripeSignatureValidator.ParsedSignature("1614556828", "abc123def456");
            assertThat(parsed).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should return null for missing t= component")
        void returnsNullForMissingTimestamp() {
            var parsed = validator.parseSignature("v1=abc123");

            assertThat(parsed).isNull();
        }

        @Test
        @DisplayName("should return null for missing v1= component")
        void returnsNullForMissingV1() {
            var parsed = validator.parseSignature("t=1614556828");

            assertThat(parsed).isNull();
        }

        @Test
        @DisplayName("should handle extra unknown components")
        void handlesExtraComponents() {
            var parsed = validator.parseSignature("t=1614556828,v1=abc123,v0=old_sig");

            var expected = new StripeSignatureValidator.ParsedSignature("1614556828", "abc123");
            assertThat(parsed).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("computeHmac")
    class ComputeHmac {

        @Test
        @DisplayName("should produce deterministic HMAC for same inputs")
        void producesDeterministicHmac() {
            var hmac1 = validator.computeHmac("12345", "test_payload");
            var hmac2 = validator.computeHmac("12345", "test_payload");

            assertThat(hmac1).isEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different payloads")
        void producesDifferentHmacForDifferentPayloads() {
            var hmac1 = validator.computeHmac("12345", "payload_a");
            var hmac2 = validator.computeHmac("12345", "payload_b");

            assertThat(hmac1).isNotEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different timestamps")
        void producesDifferentHmacForDifferentTimestamps() {
            var hmac1 = validator.computeHmac("12345", "test_payload");
            var hmac2 = validator.computeHmac("67890", "test_payload");

            assertThat(hmac1).isNotEqualTo(hmac2);
        }
    }
}
