package com.stablecoin.payments.offramp.domain.port;

/**
 * Port for stablecoin redemption (e.g., Circle USDC redemption).
 * Converts stablecoins to fiat currency.
 */
public interface RedemptionGateway {

    RedemptionResult redeem(RedemptionRequest request);
}
