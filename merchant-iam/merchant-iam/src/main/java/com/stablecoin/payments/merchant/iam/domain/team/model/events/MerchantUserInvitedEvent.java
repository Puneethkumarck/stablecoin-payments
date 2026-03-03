package com.stablecoin.payments.merchant.iam.domain.team.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MerchantUserInvitedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        UUID userId,
        String email,
        UUID roleId,
        UUID invitedBy,
        Instant occurredAt
) {
    public static final String TOPIC = "merchant.user.invited";
    public static final String EVENT_TYPE = "merchant.user.invited";
}
