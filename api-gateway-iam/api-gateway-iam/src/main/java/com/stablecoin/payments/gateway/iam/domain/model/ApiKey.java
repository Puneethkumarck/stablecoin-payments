package com.stablecoin.payments.gateway.iam.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class ApiKey {

    private final UUID keyId;
    private final UUID merchantId;
    private final String keyHash;
    private final String keyPrefix;
    private final String name;
    private final ApiKeyEnvironment environment;
    private final List<String> scopes;
    private final List<String> allowedIps;
    private boolean active;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public void revoke() {
        if (!active) {
            throw new IllegalStateException("API key is already revoked");
        }
        this.active = false;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return active && !isExpired();
    }

    public boolean hasScope(String scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return scopes != null && scopes.contains(scope);
    }

    public boolean isIpAllowed(String ip) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        return allowedIps.contains(ip);
    }
}
