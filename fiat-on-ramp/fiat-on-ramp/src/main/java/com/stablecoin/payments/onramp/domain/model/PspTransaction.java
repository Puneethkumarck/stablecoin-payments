package com.stablecoin.payments.onramp.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event log recording PSP interactions.
 * <p>
 * Each PSP webhook or API call response is captured as a {@code PspTransaction}.
 * No state transitions — this is an append-only audit record.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record PspTransaction(
        UUID pspTxnId,
        UUID collectionId,
        String pspName,
        String pspReference,
        PspTransactionDirection direction,
        String eventType,
        Money amount,
        String status,
        String rawResponse,
        Instant receivedAt
) {

    // -- Factory Method ---------------------------------------------------

    /**
     * Creates a new PSP transaction record.
     */
    public static PspTransaction create(UUID collectionId, String pspName,
                                        String pspReference, PspTransactionDirection direction,
                                        String eventType, Money amount,
                                        String status, String rawResponse) {
        if (collectionId == null) {
            throw new IllegalArgumentException("collectionId is required");
        }
        if (pspName == null || pspName.isBlank()) {
            throw new IllegalArgumentException("pspName is required");
        }
        if (pspReference == null || pspReference.isBlank()) {
            throw new IllegalArgumentException("pspReference is required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }

        return PspTransaction.builder()
                .pspTxnId(UUID.randomUUID())
                .collectionId(collectionId)
                .pspName(pspName)
                .pspReference(pspReference)
                .direction(direction)
                .eventType(eventType)
                .amount(amount)
                .status(status)
                .rawResponse(rawResponse)
                .receivedAt(Instant.now())
                .build();
    }
}
