package com.stablecoin.payments.offramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FiatPayoutInitiatedEvent(
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal fiatAmount,
        String targetCurrency,
        String paymentRail,
        String partner,
        Instant initiatedAt
) {}
