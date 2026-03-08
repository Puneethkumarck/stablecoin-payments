package com.stablecoin.payments.onramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CollectionCompletedEvent(
        UUID collectionId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal collectedAmount,
        String currency,
        String paymentRail,
        String psp,
        String pspReference,
        Instant collectedAt
) {

    public static final String TOPIC = "fiat.collected";
}
