package com.stablecoin.payments.custody.infrastructure.provider.fireblocks;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.custody.fireblocks")
public record FireblocksProperties(
        String baseUrl,
        String apiKey,
        String apiSecret,
        String vaultAccountId,
        int timeoutSeconds
) {

    public FireblocksProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.fireblocks.io";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "fireblocks-api-key";
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            apiSecret = "";
        }
        if (vaultAccountId == null || vaultAccountId.isBlank()) {
            vaultAccountId = "0";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}
