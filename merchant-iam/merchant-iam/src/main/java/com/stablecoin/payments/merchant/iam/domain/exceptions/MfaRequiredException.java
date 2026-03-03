package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class MfaRequiredException extends RuntimeException {

    private final UUID userId;
    private final UUID sessionId;

    public MfaRequiredException(UUID userId, UUID sessionId) {
        super("MFA verification required for user: " + userId);
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
