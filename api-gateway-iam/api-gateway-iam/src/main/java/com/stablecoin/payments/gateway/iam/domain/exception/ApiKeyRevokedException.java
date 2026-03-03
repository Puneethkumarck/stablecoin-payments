package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class ApiKeyRevokedException extends RuntimeException {

    private ApiKeyRevokedException(String message) {
        super(message);
    }

    public static ApiKeyRevokedException of(UUID keyId) {
        return new ApiKeyRevokedException("API key has been revoked: " + keyId);
    }
}
