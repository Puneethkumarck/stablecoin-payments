package com.stablecoin.payments.compliance.domain.exception;

import java.util.UUID;

public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(UUID paymentId) {
        super("Compliance check already exists for payment " + paymentId);
    }
}
