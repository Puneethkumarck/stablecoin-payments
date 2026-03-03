package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException {

    private MerchantNotFoundException(String message) {
        super(message);
    }

    public static MerchantNotFoundException byId(UUID merchantId) {
        return new MerchantNotFoundException("Merchant not found: " + merchantId);
    }

    public static MerchantNotFoundException byExternalId(UUID externalId) {
        return new MerchantNotFoundException("Merchant not found by external ID: " + externalId);
    }
}
