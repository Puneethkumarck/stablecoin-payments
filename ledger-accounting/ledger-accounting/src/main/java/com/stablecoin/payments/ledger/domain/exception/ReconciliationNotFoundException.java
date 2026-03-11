package com.stablecoin.payments.ledger.domain.exception;

import java.util.UUID;

public class ReconciliationNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "LD-1003";

    public ReconciliationNotFoundException(UUID paymentId) {
        super("Reconciliation record not found for payment: " + paymentId);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
