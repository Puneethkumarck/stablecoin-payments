package com.stablecoin.payments.offramp.domain.event;

import java.time.Instant;
import java.util.UUID;

public record FiatPayoutFailedEvent(
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        String reason,
        String errorCode,
        Instant failedAt
) {

    public static final String TOPIC = "fiat.payout.failed";
}
