package com.stablecoin.payments.gateway.iam.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class RateLimitEvent {

    private final UUID eventId;
    private final UUID merchantId;
    private final String endpoint;
    private final RateLimitTier tier;
    private final int requestCount;
    private final int limitValue;
    private final boolean breached;
    private final Instant occurredAt;
}
