package com.stablecoin.payments.gateway.iam.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Merchant {

    private final UUID merchantId;
    private final UUID externalId;
    private final String name;
    private final String country;
    private final List<String> scopes;
    private final List<Corridor> corridors;
    private MerchantStatus status;
    private KybStatus kybStatus;
    private RateLimitTier rateLimitTier;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public void activate() {
        if (status != MerchantStatus.PENDING) {
            throw new IllegalStateException("Only PENDING merchants can be activated, current: " + status);
        }
        this.status = MerchantStatus.ACTIVE;
        this.kybStatus = KybStatus.VERIFIED;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        if (status != MerchantStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE merchants can be suspended, current: " + status);
        }
        this.status = MerchantStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("Merchant is already closed");
        }
        this.status = MerchantStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public boolean hasScope(String scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return scopes != null && scopes.contains(scope);
    }

    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }

    public RateLimitPolicy rateLimitPolicy() {
        return new RateLimitPolicy(rateLimitTier != null ? rateLimitTier : RateLimitTier.STARTER);
    }
}
