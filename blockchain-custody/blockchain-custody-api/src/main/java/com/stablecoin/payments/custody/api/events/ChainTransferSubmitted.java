package com.stablecoin.payments.custody.api.events;

import java.time.Instant;
import java.util.UUID;

public record ChainTransferSubmitted(
        String schemaVersion,
        UUID eventId,
        String eventType,
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String stablecoin,
        String amount,
        String txHash,
        String fromAddress,
        String toAddress,
        Instant submittedAt
) {
    public static final String EVENT_TYPE = "chain.transfer.submitted";
    public static final String SCHEMA_VERSION = "1.0";
}
