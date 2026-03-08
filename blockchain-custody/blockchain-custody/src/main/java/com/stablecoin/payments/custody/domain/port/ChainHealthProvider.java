package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;

/**
 * Port for retrieving the current health score of a blockchain chain.
 * <p>
 * Returns 0.0 for unhealthy/down chains and 1.0 for healthy chains.
 */
public interface ChainHealthProvider {

    /**
     * Returns the health score for the given chain.
     *
     * @param chainId the chain to evaluate
     * @return 0.0 (unhealthy) or 1.0 (healthy)
     */
    double getHealthScore(ChainId chainId);
}
