package com.stablecoin.payments.merchant.iam.domain.team.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record UserSession(
        UUID sessionId,
        UUID userId,
        UUID merchantId,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        Instant lastActiveAt,
        boolean revoked,
        Instant revokedAt,
        String revokeReason
) {

    public UserSession revoke(String reason) {
        return toBuilder()
                .revoked(true)
                .revokedAt(Instant.now())
                .revokeReason(reason)
                .build();
    }

    public UserSession touch() {
        return toBuilder()
                .lastActiveAt(Instant.now())
                .build();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
