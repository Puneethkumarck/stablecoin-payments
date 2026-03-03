package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.RateLimitPolicy;

import java.util.UUID;

public interface RateLimiter {

    RateLimitResult check(UUID merchantId, String endpoint, RateLimitPolicy policy);

    record RateLimitResult(boolean allowed, int currentCount, int limit, String window) {}
}
