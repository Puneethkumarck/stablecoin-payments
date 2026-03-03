package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class ApiKeyExpiredException extends RuntimeException {

    private ApiKeyExpiredException(String message) {
        super(message);
    }

    public static ApiKeyExpiredException of(UUID keyId) {
        return new ApiKeyExpiredException("API key has expired: " + keyId);
    }
}
