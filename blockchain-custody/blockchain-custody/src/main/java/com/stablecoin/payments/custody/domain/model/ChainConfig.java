package com.stablecoin.payments.custody.domain.model;

import java.util.List;

public record ChainConfig(
        ChainId chainId,
        int minConfirmations,
        int avgFinalitySeconds,
        String nativeToken,
        List<String> rpcEndpoints,
        String explorerUrl
) {

    public ChainConfig {
        if (chainId == null) {
            throw new IllegalArgumentException("Chain ID is required");
        }
        if (minConfirmations < 0) {
            throw new IllegalArgumentException("Min confirmations must be non-negative");
        }
        if (avgFinalitySeconds < 0) {
            throw new IllegalArgumentException("Avg finality seconds must be non-negative");
        }
        if (nativeToken == null || nativeToken.isBlank()) {
            throw new IllegalArgumentException("Native token is required");
        }
        if (rpcEndpoints == null || rpcEndpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one RPC endpoint is required");
        }
        rpcEndpoints = List.copyOf(rpcEndpoints);
    }
}
