package com.stablecoin.payments.offramp.infrastructure.provider.circle;

/**
 * ACL DTO for Circle Business Account Payout API request.
 * Package-private — never leaks to domain.
 */
record CirclePayoutRequest(
        String idempotencyKey,
        CircleDestination destination,
        CircleAmount amount
) {

    record CircleDestination(String type, String id) {}

    record CircleAmount(String amount, String currency) {}
}
