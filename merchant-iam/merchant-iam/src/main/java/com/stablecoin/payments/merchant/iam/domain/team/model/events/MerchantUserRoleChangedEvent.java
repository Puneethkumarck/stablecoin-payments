package com.stablecoin.payments.merchant.iam.domain.team.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MerchantUserRoleChangedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        UUID userId,
        UUID previousRoleId,
        UUID newRoleId,
        UUID changedBy,
        Instant occurredAt
) {
    public static final String TOPIC = "merchant.user.role.changed";
    public static final String EVENT_TYPE = "merchant.user.role.changed";
}
