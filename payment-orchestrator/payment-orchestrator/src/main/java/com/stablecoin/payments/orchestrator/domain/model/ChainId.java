package com.stablecoin.payments.orchestrator.domain.model;

import java.util.Set;

/**
 * Value object representing a blockchain network identifier.
 * <p>
 * Supported chains: ethereum, solana, stellar, tron, base, polygon.
 */
public record ChainId(String value) {

    private static final Set<String> SUPPORTED_CHAINS =
            Set.of("ethereum", "solana", "stellar", "tron", "base", "polygon");

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
