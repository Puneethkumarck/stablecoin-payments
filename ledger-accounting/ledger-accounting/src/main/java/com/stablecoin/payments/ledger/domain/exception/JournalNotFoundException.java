package com.stablecoin.payments.ledger.domain.exception;

import java.util.UUID;

public class JournalNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "LD-1001";

    public JournalNotFoundException(UUID paymentId) {
        super("Journal not found for payment: " + paymentId);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
