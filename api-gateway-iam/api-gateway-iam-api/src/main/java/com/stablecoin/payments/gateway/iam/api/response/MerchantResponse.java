package com.stablecoin.payments.gateway.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MerchantResponse(
        UUID merchantId,
        UUID externalId,
        String name,
        String country,
        List<String> scopes,
        String status,
        String kybStatus,
        String rateLimitTier,
        Instant createdAt
) {}
