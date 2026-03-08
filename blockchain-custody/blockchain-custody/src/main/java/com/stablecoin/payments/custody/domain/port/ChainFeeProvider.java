package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;

/**
 * Port for estimating transaction fees on a given blockchain chain.
 */
public interface ChainFeeProvider {

    /**
     * Estimates the fee in USD for transferring a stablecoin on the given chain.
     *
     * @param chainId    the target chain
     * @param stablecoin the stablecoin to transfer
     * @return estimated fee in USD
     */
    double estimateFeeUsd(ChainId chainId, StablecoinTicker stablecoin);
}
