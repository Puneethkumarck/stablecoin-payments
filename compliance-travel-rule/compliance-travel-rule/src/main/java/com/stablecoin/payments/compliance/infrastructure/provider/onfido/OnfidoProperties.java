package com.stablecoin.payments.compliance.infrastructure.provider.onfido;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kyc.onfido")
public record OnfidoProperties(
        String baseUrl,
        String apiToken,
        int timeoutSeconds,
        int cacheTtlMinutes
) {
    public OnfidoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.eu.onfido.com/v3.6";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 2;
        }
        if (cacheTtlMinutes <= 0) {
            cacheTtlMinutes = 60;
        }
    }
}
