package com.stablecoin.payments.merchant.iam.api.response;

public record MfaSetupResponse(String secret, String provisioningUri) {}
