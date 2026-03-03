package com.stablecoin.payments.gateway.iam.api.response;

public record TokenResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        String scope
) {}
