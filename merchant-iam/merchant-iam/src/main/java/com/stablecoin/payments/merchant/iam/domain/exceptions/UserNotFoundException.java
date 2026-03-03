package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }

    public UserNotFoundException(UUID merchantId, String emailHash) {
        super("User not found for merchant=%s emailHash=%s".formatted(merchantId, emailHash));
    }
}
