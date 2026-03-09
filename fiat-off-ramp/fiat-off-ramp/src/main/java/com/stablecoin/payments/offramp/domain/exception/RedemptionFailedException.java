package com.stablecoin.payments.offramp.domain.exception;

import java.util.UUID;

/**
 * Thrown when stablecoin redemption fails.
 */
public class RedemptionFailedException extends RuntimeException {

    public static final String ERROR_CODE = "FR-2002";

    public RedemptionFailedException(UUID payoutId, String reason) {
        super("Stablecoin redemption failed for payout %s: %s".formatted(payoutId, reason));
    }

    public RedemptionFailedException(UUID payoutId, String reason, Throwable cause) {
        super("Stablecoin redemption failed for payout %s: %s".formatted(payoutId, reason), cause);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
