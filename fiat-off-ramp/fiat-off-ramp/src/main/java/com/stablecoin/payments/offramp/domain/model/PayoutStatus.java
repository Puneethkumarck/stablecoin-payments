package com.stablecoin.payments.offramp.domain.model;

public enum PayoutStatus {
    PENDING,
    REDEEMING,
    REDEEMED,
    REDEMPTION_FAILED,
    PAYOUT_INITIATED,
    PAYOUT_PROCESSING,
    COMPLETED,
    PAYOUT_FAILED,
    MANUAL_REVIEW,
    STABLECOIN_HELD
}
