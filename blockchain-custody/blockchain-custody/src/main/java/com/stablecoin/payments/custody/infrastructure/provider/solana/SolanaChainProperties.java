package com.stablecoin.payments.custody.infrastructure.provider.solana;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.custody.solana")
public record SolanaChainProperties(
        boolean enabled,
        String rpcUrl,
        String usdcMintAddress,
        String commitment,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {

    public SolanaChainProperties {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            rpcUrl = "https://api.devnet.solana.com";
        }
        if (usdcMintAddress == null || usdcMintAddress.isBlank()) {
            usdcMintAddress = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";
        }
        if (commitment == null || commitment.isBlank()) {
            commitment = "confirmed";
        }
        if (connectTimeoutMs == null || connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs == null || readTimeoutMs <= 0) {
            readTimeoutMs = 10000;
        }
    }
}
