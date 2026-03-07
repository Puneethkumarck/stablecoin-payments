package com.stablecoin.payments.compliance.domain.exception;

import java.util.UUID;

public class CheckNotFoundException extends RuntimeException {

    public CheckNotFoundException(UUID checkId) {
        super("Compliance check not found: " + checkId);
    }
}
