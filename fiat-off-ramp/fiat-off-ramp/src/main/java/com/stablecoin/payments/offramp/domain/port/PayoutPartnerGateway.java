package com.stablecoin.payments.offramp.domain.port;

/**
 * Port for fiat payout via off-ramp partners (e.g., Modulr SEPA, CurrencyCloud).
 * Initiates the actual fiat disbursement to the recipient's bank or mobile money account.
 */
public interface PayoutPartnerGateway {

    PayoutResult initiatePayout(PayoutRequest request);
}
