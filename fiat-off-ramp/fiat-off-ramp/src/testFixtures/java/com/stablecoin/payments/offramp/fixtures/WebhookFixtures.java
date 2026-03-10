package com.stablecoin.payments.offramp.fixtures;

import com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand.EVENT_PAYMENT_FAILED;
import static com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand.EVENT_PAYMENT_SETTLED;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.EXPECTED_FIAT_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PARTNER_REFERENCE;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.TARGET_CURRENCY;

public final class WebhookFixtures {

    private WebhookFixtures() {}

    public static final String PARTNER_NAME = "modulr";
    public static final Instant SETTLED_AT = Instant.parse("2026-03-10T14:30:00Z");

    public static String nextEventId() {
        return "evt_" + UUID.randomUUID();
    }

    public static PartnerWebhookCommand aSettlementCommand() {
        var eventId = nextEventId();
        return new PartnerWebhookCommand(
                eventId,
                EVENT_PAYMENT_SETTLED,
                PARTNER_NAME,
                PARTNER_REFERENCE,
                EXPECTED_FIAT_AMOUNT,
                TARGET_CURRENCY,
                "SETTLED",
                SETTLED_AT,
                null,
                settlementPayload(eventId)
        );
    }

    public static PartnerWebhookCommand aFailureCommand() {
        var eventId = nextEventId();
        return new PartnerWebhookCommand(
                eventId,
                EVENT_PAYMENT_FAILED,
                PARTNER_NAME,
                PARTNER_REFERENCE,
                EXPECTED_FIAT_AMOUNT,
                TARGET_CURRENCY,
                "FAILED",
                null,
                "Insufficient funds in beneficiary account",
                failurePayload(eventId)
        );
    }

    public static PartnerWebhookCommand anUnknownEventCommand() {
        var eventId = nextEventId();
        return new PartnerWebhookCommand(
                eventId,
                "payment.unknown",
                PARTNER_NAME,
                PARTNER_REFERENCE,
                null,
                null,
                null,
                null,
                null,
                "{\"event_id\":\"%s\",\"event_type\":\"payment.unknown\"}".formatted(eventId)
        );
    }

    public static String settlementPayload(String eventId) {
        return """
                {
                    "event_id": "%s",
                    "event_type": "payment.settled",
                    "payment_reference": "%s",
                    "amount": "%s",
                    "currency": "%s",
                    "status": "SETTLED",
                    "settled_at": "%s"
                }
                """.formatted(eventId, PARTNER_REFERENCE,
                EXPECTED_FIAT_AMOUNT.toPlainString(), TARGET_CURRENCY,
                SETTLED_AT.toString());
    }

    public static String failurePayload(String eventId) {
        return """
                {
                    "event_id": "%s",
                    "event_type": "payment.failed",
                    "payment_reference": "%s",
                    "amount": "%s",
                    "currency": "%s",
                    "status": "FAILED",
                    "failure_reason": "Insufficient funds in beneficiary account"
                }
                """.formatted(eventId, PARTNER_REFERENCE,
                EXPECTED_FIAT_AMOUNT.toPlainString(), TARGET_CURRENCY);
    }

    public static String signatureHeader(String timestamp, String hmac) {
        return "t=" + timestamp + ",v1=" + hmac;
    }
}
