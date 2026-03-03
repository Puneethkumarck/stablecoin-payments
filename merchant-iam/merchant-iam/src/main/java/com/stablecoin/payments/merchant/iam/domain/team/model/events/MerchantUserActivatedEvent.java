package com.stablecoin.payments.merchant.iam.domain.team.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MerchantUserActivatedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        UUID userId,
        String email,
        UUID roleId,
        Instant occurredAt
) {
    public static final String TOPIC = "merchant.user.activated";
    public static final String EVENT_TYPE = "merchant.user.activated";
}
