package com.stablecoin.payments.custody.api.events;

import java.time.Instant;
import java.util.UUID;

public record ChainTransferConfirmed(
        String schemaVersion,
        UUID eventId,
        String eventType,
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String txHash,
        String blockNumber,
        Integer confirmations,
        String gasUsed,
        Instant confirmedAt
) {
    public static final String EVENT_TYPE = "chain.transfer.confirmed";
    public static final String SCHEMA_VERSION = "1.0";
}
