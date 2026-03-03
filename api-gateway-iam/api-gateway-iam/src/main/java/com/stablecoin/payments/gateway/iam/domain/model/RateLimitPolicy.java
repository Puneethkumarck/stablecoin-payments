package com.stablecoin.payments.gateway.iam.domain.model;

import java.util.Objects;

public record RateLimitPolicy(RateLimitTier tier) {

    public RateLimitPolicy {
        Objects.requireNonNull(tier, "tier must not be null");
    }

    public int requestsPerMinute() {
        return tier.requestsPerMinute();
    }

    public int requestsPerDay() {
        return tier.requestsPerDay();
    }

    public boolean isUnlimited() {
        return tier == RateLimitTier.UNLIMITED;
    }
}
