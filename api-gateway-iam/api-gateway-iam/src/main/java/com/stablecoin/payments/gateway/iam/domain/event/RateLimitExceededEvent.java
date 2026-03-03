package com.stablecoin.payments.gateway.iam.domain.event;

import java.time.Instant;
import java.util.UUID;

public record RateLimitExceededEvent(
        UUID merchantId,
        String endpoint,
        String tier,
        int requestCount,
        int limit,
        Instant occurredAt
) {}
