package com.stablecoin.payments.gateway.iam.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyRevokedEvent(
        UUID keyId,
        UUID merchantId,
        String keyPrefix,
        Instant revokedAt
) {}
