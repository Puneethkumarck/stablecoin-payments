package com.stablecoin.payments.custody.api.events;

import java.time.Instant;
import java.util.UUID;

public record ChainTransferFailed(
        String schemaVersion,
        UUID eventId,
        String eventType,
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String reason,
        String errorCode,
        Instant failedAt
) {
    public static final String EVENT_TYPE = "chain.transfer.failed";
    public static final String SCHEMA_VERSION = "1.0";
}
