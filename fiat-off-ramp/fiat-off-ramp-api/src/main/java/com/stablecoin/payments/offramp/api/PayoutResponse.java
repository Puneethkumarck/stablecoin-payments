package com.stablecoin.payments.offramp.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID payoutId,
        UUID paymentId,
        String status,
        String payoutType,
        BigDecimal fiatAmount,
        String targetCurrency,
        String paymentRail,
        String partner,
        String partnerReference,
        Instant partnerSettledAt,
        Instant createdAt,
        Instant completedAt
) {}
