package com.stablecoin.payments.gateway.iam.domain.model;

public enum RateLimitTier {

    STARTER(60, 10_000),
    GROWTH(300, 100_000),
    ENTERPRISE(1_000, 1_000_000),
    UNLIMITED(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int requestsPerMinute;
    private final int requestsPerDay;

    RateLimitTier(int requestsPerMinute, int requestsPerDay) {
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerDay = requestsPerDay;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public int requestsPerDay() {
        return requestsPerDay;
    }
}
