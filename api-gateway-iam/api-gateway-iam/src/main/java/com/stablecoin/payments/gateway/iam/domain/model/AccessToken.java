package com.stablecoin.payments.gateway.iam.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class AccessToken {

    private final UUID jti;
    private final UUID merchantId;
    private final UUID clientId;
    private final List<String> scopes;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private boolean revoked;
    private Instant revokedAt;

    public void revoke() {
        if (revoked) {
            throw new IllegalStateException("Token is already revoked");
        }
        this.revoked = true;
        this.revokedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
