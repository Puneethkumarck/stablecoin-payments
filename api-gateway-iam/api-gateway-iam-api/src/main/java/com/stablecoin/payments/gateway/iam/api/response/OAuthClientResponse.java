package com.stablecoin.payments.gateway.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OAuthClientResponse(
        UUID clientId,
        String clientSecret,
        UUID merchantId,
        String name,
        List<String> scopes,
        List<String> grantTypes,
        Instant createdAt
) {}
