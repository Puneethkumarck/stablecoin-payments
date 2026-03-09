package com.stablecoin.payments.custody.domain.port;

/**
 * Domain port for per-chain confirmation configuration.
 */
public interface ChainConfirmationProperties {

    /**
     * Returns the minimum confirmations required for a given chain.
     *
     * @param chainId the chain identifier (e.g., "base", "ethereum", "solana")
     * @return minimum confirmations (always positive)
     * @throws IllegalStateException if chain is not configured
     */
    int getMinConfirmations(String chainId);
}
