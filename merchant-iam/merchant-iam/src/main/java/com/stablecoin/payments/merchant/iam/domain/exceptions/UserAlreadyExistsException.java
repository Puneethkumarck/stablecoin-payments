package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(UUID merchantId, String email) {
        super("User already exists for merchant=%s email=%s".formatted(merchantId, email));
    }
}
