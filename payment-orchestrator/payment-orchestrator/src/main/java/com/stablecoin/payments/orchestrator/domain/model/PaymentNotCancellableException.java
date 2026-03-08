package com.stablecoin.payments.orchestrator.domain.model;

import java.util.UUID;

/**
 * Thrown when attempting to cancel a payment that is in a terminal state
 * and cannot be cancelled.
 */
public class PaymentNotCancellableException extends RuntimeException {

    public PaymentNotCancellableException(UUID paymentId, PaymentState state) {
        super("Payment %s cannot be cancelled in state %s".formatted(paymentId, state));
    }
}
