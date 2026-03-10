package com.stablecoin.payments.offramp.domain.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command representing a parsed webhook notification from an off-ramp partner.
 *
 * @param eventId          unique event ID from the partner (for audit)
 * @param eventType        event type: "payment.settled" or "payment.failed"
 * @param partnerName      the partner name (e.g., "modulr")
 * @param partnerReference the partner's payment reference
 * @param amount           the settlement amount
 * @param currency         the settlement currency
 * @param status           the payment status string from the partner
 * @param settledAt        the settlement timestamp (null for failures)
 * @param failureReason    failure reason (null for success)
 * @param rawPayload       the raw JSON payload for audit trail
 */
public record PartnerWebhookCommand(
        String eventId,
        String eventType,
        String partnerName,
        String partnerReference,
        BigDecimal amount,
        String currency,
        String status,
        Instant settledAt,
        String failureReason,
        String rawPayload
) {

    public static final String EVENT_PAYMENT_SETTLED = "payment.settled";
    public static final String EVENT_PAYMENT_FAILED = "payment.failed";

    public PartnerWebhookCommand {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (partnerName == null || partnerName.isBlank()) {
            throw new IllegalArgumentException("partnerName is required");
        }
        if (partnerReference == null || partnerReference.isBlank()) {
            throw new IllegalArgumentException("partnerReference is required");
        }
    }
}
