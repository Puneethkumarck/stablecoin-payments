package com.stablecoin.payments.gateway.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OAuthClientSummaryResponse(
        UUID clientId,
        UUID merchantId,
        String name,
        List<String> scopes,
        List<String> grantTypes,
        boolean active,
        Instant createdAt
) {}
