package com.stablecoin.payments.onramp.fixtures;

import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.service.WebhookCommand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;

public final class WebhookFixtures {

    private WebhookFixtures() {}

    // -- Constants --------------------------------------------------------

    public static final String WEBHOOK_SECRET = "whsec_test_secret_12345";
    public static final String EVENT_ID = "evt_test_001";
    public static final String EVENT_TYPE_SUCCEEDED = "payment_intent.succeeded";
    public static final String EVENT_TYPE_FAILED = "payment_intent.payment_failed";
    public static final UUID COLLECTION_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    // -- Stripe Event JSON -----------------------------------------------

    public static String aSucceededEventJson() {
        return aSucceededEventJson(PSP_REFERENCE, 100000L, "usd", COLLECTION_ID);
    }

    public static String aSucceededEventJson(String pspRef, long amountCents,
                                              String currency, UUID collectionId) {
        return """
                {
                  "id": "%s",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "amount": %d,
                      "currency": "%s",
                      "status": "succeeded",
                      "metadata": {
                        "collection_id": "%s"
                      }
                    }
                  }
                }
                """.formatted(EVENT_ID, pspRef, amountCents, currency, collectionId);
    }

    public static String aFailedEventJson() {
        return aFailedEventJson(PSP_REFERENCE, COLLECTION_ID);
    }

    public static String aFailedEventJson(String pspRef, UUID collectionId) {
        return """
                {
                  "id": "%s",
                  "type": "payment_intent.payment_failed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "amount": 100000,
                      "currency": "usd",
                      "status": "failed",
                      "metadata": {
                        "collection_id": "%s"
                      },
                      "last_payment_error": {
                        "message": "Your bank account could not be verified."
                      }
                    }
                  }
                }
                """.formatted(EVENT_ID, pspRef, collectionId);
    }

    public static String aMismatchAmountEventJson() {
        return aSucceededEventJson(PSP_REFERENCE, 50000L, "usd", COLLECTION_ID);
    }

    // -- WebhookCommand factories ----------------------------------------

    public static WebhookCommand aSucceededCommand() {
        return new WebhookCommand(
                EVENT_ID,
                EVENT_TYPE_SUCCEEDED,
                PSP_REFERENCE,
                COLLECTION_ID,
                new Money(new BigDecimal("1000.00"), "USD"),
                "succeeded",
                aSucceededEventJson());
    }

    public static WebhookCommand aFailedCommand() {
        return new WebhookCommand(
                EVENT_ID,
                EVENT_TYPE_FAILED,
                PSP_REFERENCE,
                COLLECTION_ID,
                new Money(new BigDecimal("1000.00"), "USD"),
                "failed",
                aFailedEventJson());
    }

    public static WebhookCommand aMismatchCommand() {
        return new WebhookCommand(
                EVENT_ID,
                EVENT_TYPE_SUCCEEDED,
                PSP_REFERENCE,
                COLLECTION_ID,
                new Money(new BigDecimal("500.00"), "USD"),
                "succeeded",
                aMismatchAmountEventJson());
    }

    // -- Signature helpers -----------------------------------------------

    public static String computeSignature(String payload, String secret) {
        return computeSignature(payload, secret, Instant.now());
    }

    public static String computeSignature(String payload, String secret, Instant timestamp) {
        var timestampSeconds = String.valueOf(timestamp.getEpochSecond());
        var hmac = computeHmac(timestampSeconds, payload, secret);
        return "t=" + timestampSeconds + ",v1=" + hmac;
    }

    private static String computeHmac(String timestamp, String payload, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var signedPayload = timestamp + "." + payload;
            var hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
