package com.stablecoin.payments.merchant.iam.domain.exceptions;

import java.util.UUID;

public class LastAdminException extends RuntimeException {

    public LastAdminException(UUID merchantId) {
        super("Cannot remove or demote the last admin for merchant: " + merchantId);
    }
}
