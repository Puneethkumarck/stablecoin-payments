package com.stablecoin.payments.offramp.api.events;

import java.time.Instant;
import java.util.UUID;

public record FiatPayoutFailed(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        String reason,
        String errorCode,
        Instant failedAt
) {
    public static final String EVENT_TYPE = "fiat.payout.failed";
    public static final int SCHEMA_VERSION = 1;
}
