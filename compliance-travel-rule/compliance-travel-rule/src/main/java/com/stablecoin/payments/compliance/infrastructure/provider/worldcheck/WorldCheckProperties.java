package com.stablecoin.payments.compliance.infrastructure.provider.worldcheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sanctions.world-check")
public record WorldCheckProperties(
        String baseUrl,
        String apiKey,
        String apiSecret,
        String groupId,
        int timeoutSeconds
) {
    public WorldCheckProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://rms-world-check-one-api-pilot.thomsonreuters.com/v2";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 2;
        }
    }
}
