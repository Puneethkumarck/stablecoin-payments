package com.stablecoin.payments.offramp.domain.model;

public enum PayoutTrigger {
    START_REDEMPTION,
    COMPLETE_REDEMPTION,
    FAIL_REDEMPTION,
    INITIATE_PAYOUT,
    MARK_PAYOUT_PROCESSING,
    COMPLETE_PAYOUT,
    FAIL_PAYOUT,
    ESCALATE_MANUAL_REVIEW,
    HOLD_STABLECOIN,
    COMPLETE_HOLD
}
