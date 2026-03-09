package com.stablecoin.payments.offramp.domain.exception;

import java.util.UUID;

/**
 * Thrown when a payout order cannot be found by the given identifier.
 */
public class PayoutNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "FR-1003";

    public PayoutNotFoundException(String identifier) {
        super("Payout order not found: " + identifier);
    }

    public PayoutNotFoundException(UUID payoutId) {
        super("Payout order not found: " + payoutId);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
