package com.stablecoin.payments.custody.infrastructure.provider.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.custody.dev")
public record DevCustodyProperties(
        String evmPrivateKey,
        long baseChainId,
        long ethereumChainId,
        String baseRpcUrl,
        String ethereumRpcUrl,
        String solanaRpcUrl,
        String baseUsdcContract,
        String ethereumUsdcContract,
        long gasPrice,
        long gasLimit,
        int connectTimeoutMs,
        int readTimeoutMs
) {

    public DevCustodyProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 10000;
        }
        if (gasPrice <= 0) {
            gasPrice = 1_000_000_000L;
        }
        if (gasLimit <= 0) {
            gasLimit = 65000L;
        }
        if (baseChainId <= 0) {
            baseChainId = 84532L;
        }
        if (ethereumChainId <= 0) {
            ethereumChainId = 11155111L;
        }
    }
}
