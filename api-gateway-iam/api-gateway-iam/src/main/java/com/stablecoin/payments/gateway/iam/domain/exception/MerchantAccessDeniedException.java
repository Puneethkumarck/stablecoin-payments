package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.UUID;

public class MerchantAccessDeniedException extends RuntimeException {

    private MerchantAccessDeniedException(String message) {
        super(message);
    }

    public static MerchantAccessDeniedException forMerchant(UUID targetMerchantId) {
        return new MerchantAccessDeniedException(
                "Not authorized to access resources for merchant: " + targetMerchantId);
    }
}
