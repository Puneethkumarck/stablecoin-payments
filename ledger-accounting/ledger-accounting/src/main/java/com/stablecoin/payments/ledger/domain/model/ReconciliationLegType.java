package com.stablecoin.payments.ledger.domain.model;

public enum ReconciliationLegType {
    FIAT_IN,
    STABLECOIN_MINTED,
    CHAIN_TRANSFERRED,
    STABLECOIN_REDEEMED,
    FIAT_OUT,
    FX_RATE
}
