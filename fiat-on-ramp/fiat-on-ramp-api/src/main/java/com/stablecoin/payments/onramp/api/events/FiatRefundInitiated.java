package com.stablecoin.payments.onramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatRefundInitiated(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID refundId,
        UUID collectionId,
        UUID paymentId,
        BigDecimal refundAmount,
        String currency,
        String reason,
        Instant initiatedAt
) {
    public static final String EVENT_TYPE = "fiat.refund.initiated";
    public static final int SCHEMA_VERSION = 1;
}
