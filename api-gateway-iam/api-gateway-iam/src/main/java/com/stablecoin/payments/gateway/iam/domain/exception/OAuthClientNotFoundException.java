package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class OAuthClientNotFoundException extends RuntimeException {

    private OAuthClientNotFoundException(String message) {
        super(message);
    }

    public static OAuthClientNotFoundException byId(UUID clientId) {
        return new OAuthClientNotFoundException("OAuth client not found: " + clientId);
    }
}
