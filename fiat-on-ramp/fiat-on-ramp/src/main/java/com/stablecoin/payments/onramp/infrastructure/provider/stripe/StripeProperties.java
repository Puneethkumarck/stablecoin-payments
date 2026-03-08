package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.psp.stripe")
public record StripeProperties(String baseUrl, String apiKey, int timeoutSeconds) {

    public StripeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.stripe.com";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "sk_test_default";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
    }
}
