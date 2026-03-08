package com.stablecoin.payments.onramp.domain.exception;

import com.stablecoin.payments.onramp.domain.model.Money;

import java.util.UUID;

/**
 * Thrown when the requested refund amount exceeds the collected amount.
 */
public class RefundAmountExceededException extends RuntimeException {

    public static final String ERROR_CODE = "OR-2003";

    public RefundAmountExceededException(UUID collectionId, Money refundAmount, Money collectedAmount) {
        super("Refund amount %s %s exceeds collected amount %s %s for collection %s"
                .formatted(
                        refundAmount.amount(), refundAmount.currency(),
                        collectedAmount.amount(), collectedAmount.currency(),
                        collectionId));
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
