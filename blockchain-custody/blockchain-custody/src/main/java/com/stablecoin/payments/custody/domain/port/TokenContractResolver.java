package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;

/**
 * Domain port for resolving token contract addresses per chain and stablecoin.
 */
public interface TokenContractResolver {

    /**
     * Resolves the on-chain token contract address for the given chain and stablecoin.
     *
     * @param chainId    the blockchain identifier
     * @param stablecoin the stablecoin ticker
     * @return the contract address (e.g., ERC-20 address or SPL mint)
     * @throws IllegalStateException if no contract is configured for the given chain/stablecoin
     */
    String resolveContract(ChainId chainId, StablecoinTicker stablecoin);
}
