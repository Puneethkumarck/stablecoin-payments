package com.stablecoin.payments.merchant.iam.domain.team.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MerchantUserSuspendedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        UUID userId,
        String reason,
        UUID suspendedBy,
        Instant occurredAt
) {
    public static final String TOPIC = "merchant.user.suspended";
    public static final String EVENT_TYPE = "merchant.user.suspended";
}
