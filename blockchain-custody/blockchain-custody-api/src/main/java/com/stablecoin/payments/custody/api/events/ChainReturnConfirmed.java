package com.stablecoin.payments.custody.api.events;

import java.time.Instant;
import java.util.UUID;

public record ChainReturnConfirmed(
        String schemaVersion,
        UUID eventId,
        String eventType,
        UUID transferId,
        UUID parentTransferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String txHash,
        Instant confirmedAt
) {
    public static final String EVENT_TYPE = "chain.return.confirmed";
    public static final String SCHEMA_VERSION = "1.0";
}
