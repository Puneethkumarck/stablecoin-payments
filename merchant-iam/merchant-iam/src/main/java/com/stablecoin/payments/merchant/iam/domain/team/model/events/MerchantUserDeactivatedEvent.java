package com.stablecoin.payments.merchant.iam.domain.team.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MerchantUserDeactivatedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        UUID userId,
        String reason,
        UUID deactivatedBy,
        Instant occurredAt
) {
    public static final String TOPIC = "merchant.user.deactivated";
    public static final String EVENT_TYPE = "merchant.user.deactivated";
}
