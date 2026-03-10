package com.stablecoin.payments.offramp.infrastructure.provider.modulr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Modulr webhook HMAC validation.
 *
 * @param webhookSecret    the HMAC-SHA256 secret shared with Modulr
 * @param toleranceSeconds the timestamp tolerance window (default 300 = 5 min)
 */
@ConfigurationProperties(prefix = "app.payout.modulr.webhook")
public record ModulrWebhookProperties(
        String webhookSecret,
        int toleranceSeconds
) {

    public ModulrWebhookProperties {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            webhookSecret = "dev-webhook-secret";
        }
        if (toleranceSeconds <= 0) {
            toleranceSeconds = 300;
        }
    }
}
