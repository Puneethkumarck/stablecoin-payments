package com.stablecoin.payments.onramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CollectionInitiatedEvent(
        UUID collectionId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal amount,
        String currency,
        String paymentRail,
        String psp,
        Instant initiatedAt
) {

    public static final String TOPIC = "fiat.collection.initiated";
}
