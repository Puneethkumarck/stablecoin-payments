package com.stablecoin.payments.onramp.domain.exception;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;

import java.util.UUID;

/**
 * Thrown when a refund is requested for a collection order
 * that is not in the COLLECTED state.
 */
public class RefundNotAllowedException extends RuntimeException {

    public static final String ERROR_CODE = "OR-2002";

    public RefundNotAllowedException(UUID collectionId, CollectionStatus currentStatus) {
        super("Refund not allowed for collection %s — current status: %s (must be COLLECTED)"
                .formatted(collectionId, currentStatus));
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
