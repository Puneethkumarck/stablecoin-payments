package com.stablecoin.payments.fx.infrastructure.provider.refinitiv;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fx.refinitiv")
public record RefinitivProperties(
        String baseUrl,
        String apiKey,
        int timeoutSeconds
) {
    public RefinitivProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.refinitiv.com";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 2;
        }
    }
}
