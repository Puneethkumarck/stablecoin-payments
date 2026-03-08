package com.stablecoin.payments.custody.domain.model;

import java.util.Set;

public record ChainId(String value) {

    private static final Set<String> SUPPORTED_CHAINS =
            Set.of("ethereum", "solana", "stellar", "tron", "base", "polygon", "avalanche");

    public ChainId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Chain ID value is required");
        }
        if (!SUPPORTED_CHAINS.contains(value)) {
            throw new IllegalArgumentException(
                    "Unsupported chain: %s. Must be one of: %s".formatted(value, SUPPORTED_CHAINS));
        }
    }
}
