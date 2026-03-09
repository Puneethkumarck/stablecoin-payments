package com.stablecoin.payments.offramp.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StablecoinRedeemed(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID redemptionId,
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        String stablecoin,
        BigDecimal redeemedAmount,
        BigDecimal fiatReceived,
        String fiatCurrency,
        Instant redeemedAt
) {
    public static final String EVENT_TYPE = "stablecoin.redeemed";
    public static final int SCHEMA_VERSION = 1;
}
