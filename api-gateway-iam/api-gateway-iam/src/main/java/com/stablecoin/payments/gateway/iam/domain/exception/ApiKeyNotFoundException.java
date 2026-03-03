package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class ApiKeyNotFoundException extends RuntimeException {

    private ApiKeyNotFoundException(String message) {
        super(message);
    }

    public static ApiKeyNotFoundException byId(UUID keyId) {
        return new ApiKeyNotFoundException("API key not found: " + keyId);
    }

    public static ApiKeyNotFoundException byHash() {
        return new ApiKeyNotFoundException("API key not found for provided key");
    }
}
