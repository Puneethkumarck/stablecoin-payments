package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.model.Money;

import java.util.UUID;

/**
 * Command representing a parsed webhook notification from a PSP.
 *
 * @param eventId       the unique event ID from the PSP (for idempotency)
 * @param eventType     the event type (e.g., "payment_intent.succeeded")
 * @param pspReference  the PSP payment reference (e.g., Stripe PaymentIntent ID)
 * @param collectionId  the collection order ID from metadata (optional)
 * @param amount        the amount from the PSP event
 * @param status        the payment status string from the PSP
 * @param rawPayload    the raw JSON payload for audit trail
 */
public record WebhookCommand(
        String eventId,
        String eventType,
        String pspReference,
        UUID collectionId,
        Money amount,
        String status,
        String rawPayload
) {

    public WebhookCommand {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (pspReference == null || pspReference.isBlank()) {
            throw new IllegalArgumentException("pspReference is required");
        }
    }
}
