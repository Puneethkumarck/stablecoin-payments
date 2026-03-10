package com.stablecoin.payments.offramp.infrastructure.provider.circle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.redemption.circle")
public record CircleProperties(String baseUrl, String apiKey, String destinationId, int timeoutSeconds) {

    public CircleProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api-sandbox.circle.com";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "SAND_API_KEY_DEFAULT";
        }
        if (destinationId == null || destinationId.isBlank()) {
            destinationId = "default-wire-id";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
    }
}
