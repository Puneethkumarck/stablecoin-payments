package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class MerchantNotActiveException extends RuntimeException {

    private MerchantNotActiveException(String message) {
        super(message);
    }

    public static MerchantNotActiveException of(UUID merchantId) {
        return new MerchantNotActiveException("Merchant is not active: " + merchantId);
    }
}
