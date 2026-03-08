package com.stablecoin.payments.onramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatCollected(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID collectionId,
        UUID paymentId,
        UUID merchantId,
        BigDecimal settledAmount,
        String currency,
        String paymentRail,
        String psp,
        String pspReference,
        Instant settledAt
) {
    public static final String EVENT_TYPE = "fiat.collected";
    public static final int SCHEMA_VERSION = 1;
}
