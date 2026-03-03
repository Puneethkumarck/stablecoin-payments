package com.stablecoin.payments.gateway.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyResponse(
        UUID keyId,
        String rawKey,
        String keyPrefix,
        String name,
        String environment,
        List<String> scopes,
        List<String> allowedIps,
        Instant expiresAt,
        Instant createdAt
) {}
