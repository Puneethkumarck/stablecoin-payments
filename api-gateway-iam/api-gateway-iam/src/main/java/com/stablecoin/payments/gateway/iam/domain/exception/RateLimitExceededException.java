package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    private RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static RateLimitExceededException perMinute(UUID merchantId, int retryAfterSeconds) {
        return new RateLimitExceededException(
                "Rate limit exceeded for merchant " + merchantId + " (per-minute)",
                retryAfterSeconds);
    }

    public static RateLimitExceededException perDay(UUID merchantId) {
        return new RateLimitExceededException(
                "Daily rate limit exceeded for merchant " + merchantId,
                3600);
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
