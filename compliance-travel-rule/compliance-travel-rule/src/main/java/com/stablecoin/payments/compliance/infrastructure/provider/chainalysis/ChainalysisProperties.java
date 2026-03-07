package com.stablecoin.payments.compliance.infrastructure.provider.chainalysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aml.chainalysis")
public record ChainalysisProperties(
        String baseUrl,
        String apiKey,
        int timeoutSeconds
) {
    public ChainalysisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.chainalysis.com/api/kyt/v2";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 2;
        }
    }
}
