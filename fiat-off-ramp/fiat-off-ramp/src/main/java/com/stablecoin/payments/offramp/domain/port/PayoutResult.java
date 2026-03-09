package com.stablecoin.payments.offramp.domain.port;

import java.time.Instant;

public record PayoutResult(
        String partnerReference,
        String status,
        Instant settledAt
) {}
