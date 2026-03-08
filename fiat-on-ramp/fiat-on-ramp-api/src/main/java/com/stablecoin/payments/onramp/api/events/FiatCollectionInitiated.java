package com.stablecoin.payments.onramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatCollectionInitiated(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID collectionId,
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String paymentRail,
        String psp,
        String pspReference,
        Instant initiatedAt
) {
    public static final String EVENT_TYPE = "fiat.collection.initiated";
    public static final int SCHEMA_VERSION = 1;
}
