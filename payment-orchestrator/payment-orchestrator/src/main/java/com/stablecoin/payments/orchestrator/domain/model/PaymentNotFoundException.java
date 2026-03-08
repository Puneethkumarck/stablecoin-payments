package com.stablecoin.payments.orchestrator.domain.model;

import java.util.UUID;

/**
 * Thrown when a payment cannot be found by its ID.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }
}
