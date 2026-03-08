package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

record StripePaymentIntentResponse(
        String id,
        String status,
        Long amount,
        String currency,
        String clientSecret
) {
}
