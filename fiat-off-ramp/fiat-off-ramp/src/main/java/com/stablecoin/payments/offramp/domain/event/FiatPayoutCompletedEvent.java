package com.stablecoin.payments.offramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatPayoutCompletedEvent(
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal fiatAmount,
        String targetCurrency,
        String paymentRail,
        String partnerReference,
        Instant completedAt
) {

    public static final String TOPIC = "fiat.payout.completed";
}
