package com.stablecoin.payments.gateway.iam.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OAuthClientProvisionedEvent(
        UUID clientId,
        UUID merchantId,
        String rawClientSecret,
        String name,
        List<String> scopes,
        List<String> grantTypes,
        Instant createdAt
) {
    public static final String TOPIC = "oauth-client.provisioned";
}
