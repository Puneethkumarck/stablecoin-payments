package com.stablecoin.payments.onramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatCollectionFailed(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID collectionId,
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String failureReason,
        String errorCode,
        Instant failedAt
) {
    public static final String EVENT_TYPE = "fiat.collection.failed";
    public static final int SCHEMA_VERSION = 1;
}
