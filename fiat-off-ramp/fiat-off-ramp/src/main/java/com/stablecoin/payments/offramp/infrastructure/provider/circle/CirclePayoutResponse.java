package com.stablecoin.payments.offramp.infrastructure.provider.circle;

/**
 * ACL DTO for Circle Business Account Payout API response.
 * Package-private — never leaks to domain.
 */
record CirclePayoutResponse(CirclePayoutData data) {

    record CirclePayoutData(
            String id,
            CirclePayoutAmount amount,
            String status,
            String createDate
    ) {}

    record CirclePayoutAmount(String amount, String currency) {}
}
