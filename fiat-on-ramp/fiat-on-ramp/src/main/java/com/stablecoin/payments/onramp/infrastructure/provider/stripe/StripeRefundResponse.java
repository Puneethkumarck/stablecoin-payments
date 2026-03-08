package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

record StripeRefundResponse(
        String id,
        String status,
        Long amount,
        String currency,
        String paymentIntent
) {
}
