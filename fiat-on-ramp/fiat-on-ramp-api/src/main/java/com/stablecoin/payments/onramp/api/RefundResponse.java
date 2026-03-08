package com.stablecoin.payments.onramp.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID collectionId,
        String status,
        BigDecimal refundAmount,
        String currency,
        Instant initiatedAt,
        Instant estimatedCompletionAt
) {}
