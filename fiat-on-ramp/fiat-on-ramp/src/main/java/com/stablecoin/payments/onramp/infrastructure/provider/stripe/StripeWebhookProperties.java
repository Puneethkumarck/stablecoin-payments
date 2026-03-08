package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.psp.stripe.webhook")
public record StripeWebhookProperties(String webhookSecret, int toleranceSeconds) {

    public StripeWebhookProperties {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            webhookSecret = "whsec_test_default";
        }
        if (toleranceSeconds <= 0) {
            toleranceSeconds = 300;
        }
    }
}
