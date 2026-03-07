package com.stablecoin.payments.compliance.domain.exception;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID customerId) {
        super("Customer risk profile not found: " + customerId);
    }
}
