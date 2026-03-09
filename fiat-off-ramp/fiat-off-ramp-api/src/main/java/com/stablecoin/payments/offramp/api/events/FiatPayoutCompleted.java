package com.stablecoin.payments.offramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatPayoutCompleted(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal fiatAmount,
        String targetCurrency,
        String paymentRail,
        String partnerReference,
        Instant completedAt
) {
    public static final String EVENT_TYPE = "fiat.payout.completed";
    public static final int SCHEMA_VERSION = 1;
}
