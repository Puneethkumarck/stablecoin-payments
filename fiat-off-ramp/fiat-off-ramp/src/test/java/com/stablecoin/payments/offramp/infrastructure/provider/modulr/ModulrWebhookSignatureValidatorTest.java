package com.stablecoin.payments.offramp.infrastructure.provider.modulr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModulrWebhookSignatureValidator")
class ModulrWebhookSignatureValidatorTest {

    private static final String WEBHOOK_SECRET = "test-modulr-webhook-secret";
    private static final int TOLERANCE_SECONDS = 300;

    private ModulrWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        var properties = new ModulrWebhookProperties(WEBHOOK_SECRET, TOLERANCE_SECONDS);
        validator = new ModulrWebhookSignatureValidator(properties);
    }

    @Nested
    @DisplayName("Valid signatures")
    class ValidSignatures {

        @Test
        @DisplayName("should accept valid HMAC-SHA256 signature")
        void shouldAcceptValidSignature() {
            var payload = "{\"event_type\":\"payment.settled\"}";
            var timestamp = String.valueOf(Instant.now().getEpochSecond());
            var hmac = computeHmac(timestamp, payload);
            var signature = "t=" + timestamp + ",v1=" + hmac;

            assertThat(validator.isValid(payload, signature)).isTrue();
        }

        @Test
        @DisplayName("should accept signature at boundary of tolerance window")
        void shouldAcceptAtBoundary() {
            var payload = "{\"event_type\":\"payment.settled\"}";
            var timestamp = String.valueOf(
                    Instant.now().minusSeconds(TOLERANCE_SECONDS - 1).getEpochSecond());
            var hmac = computeHmac(timestamp, payload);
            var signature = "t=" + timestamp + ",v1=" + hmac;

            assertThat(validator.isValid(payload, signature)).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid signatures")
    class InvalidSignatures {

        @Test
        @DisplayName("should reject null payload")
        void shouldRejectNullPayload() {
            assertThat(validator.isValid(null, "t=123,v1=abc")).isFalse();
        }

        @Test
        @DisplayName("should reject null signature")
        void shouldRejectNullSignature() {
            assertThat(validator.isValid("{}", null)).isFalse();
        }

        @Test
        @DisplayName("should reject blank signature")
        void shouldRejectBlankSignature() {
            assertThat(validator.isValid("{}", "")).isFalse();
        }

        @Test
        @DisplayName("should reject tampered payload")
        void shouldRejectTamperedPayload() {
            var originalPayload = "{\"event_type\":\"payment.settled\"}";
            var timestamp = String.valueOf(Instant.now().getEpochSecond());
            var hmac = computeHmac(timestamp, originalPayload);
            var signature = "t=" + timestamp + ",v1=" + hmac;

            var tamperedPayload = "{\"event_type\":\"payment.failed\"}";
            assertThat(validator.isValid(tamperedPayload, signature)).isFalse();
        }

        @Test
        @DisplayName("should reject expired timestamp")
        void shouldRejectExpiredTimestamp() {
            var payload = "{\"event_type\":\"payment.settled\"}";
            var timestamp = String.valueOf(
                    Instant.now().minusSeconds(TOLERANCE_SECONDS + 60).getEpochSecond());
            var hmac = computeHmac(timestamp, payload);
            var signature = "t=" + timestamp + ",v1=" + hmac;

            assertThat(validator.isValid(payload, signature)).isFalse();
        }

        @Test
        @DisplayName("should reject malformed signature header (missing v1)")
        void shouldRejectMalformedSignature() {
            assertThat(validator.isValid("{}", "t=123")).isFalse();
        }

        @Test
        @DisplayName("should reject malformed signature header (missing t)")
        void shouldRejectMissingTimestamp() {
            assertThat(validator.isValid("{}", "v1=abc123")).isFalse();
        }

        @Test
        @DisplayName("should reject invalid timestamp format")
        void shouldRejectInvalidTimestampFormat() {
            assertThat(validator.isValid("{}", "t=not-a-number,v1=abc123")).isFalse();
        }

        @Test
        @DisplayName("should reject wrong secret (signature mismatch)")
        void shouldRejectWrongSecret() {
            var payload = "{\"event_type\":\"payment.settled\"}";
            var timestamp = String.valueOf(Instant.now().getEpochSecond());
            var hmac = computeHmacWithSecret(timestamp, payload, "wrong-secret");
            var signature = "t=" + timestamp + ",v1=" + hmac;

            assertThat(validator.isValid(payload, signature)).isFalse();
        }
    }

    @Nested
    @DisplayName("Signature parsing")
    class SignatureParsing {

        @Test
        @DisplayName("should parse valid signature header")
        void shouldParseValidHeader() {
            var parsed = validator.parseSignature("t=1234567890,v1=abc123def456");

            assertThat(parsed).isNotNull();
            assertThat(parsed.timestamp()).isEqualTo("1234567890");
            assertThat(parsed.v1Signature()).isEqualTo("abc123def456");
        }

        @Test
        @DisplayName("should handle extra whitespace in signature parts")
        void shouldHandleWhitespace() {
            var parsed = validator.parseSignature("t= 1234567890, v1= abc123def456");

            assertThat(parsed).isNotNull();
            assertThat(parsed.timestamp()).isEqualTo("1234567890");
            assertThat(parsed.v1Signature()).isEqualTo("abc123def456");
        }

        @Test
        @DisplayName("should return null for completely invalid header")
        void shouldReturnNullForInvalidHeader() {
            assertThat(validator.parseSignature("invalid")).isNull();
        }
    }

    @Nested
    @DisplayName("HMAC computation")
    class HmacComputation {

        @Test
        @DisplayName("should produce deterministic HMAC for same inputs")
        void shouldProduceDeterministicHmac() {
            var hmac1 = validator.computeHmac("1234567890", "test-payload");
            var hmac2 = validator.computeHmac("1234567890", "test-payload");

            assertThat(hmac1).isEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different payloads")
        void shouldProduceDifferentHmacForDifferentPayloads() {
            var hmac1 = validator.computeHmac("1234567890", "payload-1");
            var hmac2 = validator.computeHmac("1234567890", "payload-2");

            assertThat(hmac1).isNotEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMAC for different timestamps")
        void shouldProduceDifferentHmacForDifferentTimestamps() {
            var hmac1 = validator.computeHmac("1234567890", "payload");
            var hmac2 = validator.computeHmac("9876543210", "payload");

            assertThat(hmac1).isNotEqualTo(hmac2);
        }
    }

    private String computeHmac(String timestamp, String payload) {
        return computeHmacWithSecret(timestamp, payload, WEBHOOK_SECRET);
    }

    private String computeHmacWithSecret(String timestamp, String payload, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var signedPayload = timestamp + "." + payload;
            var hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
