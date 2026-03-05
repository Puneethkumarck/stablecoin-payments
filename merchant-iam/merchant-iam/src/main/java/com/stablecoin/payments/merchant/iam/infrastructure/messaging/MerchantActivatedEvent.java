package com.stablecoin.payments.merchant.iam.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound event produced by S11 Merchant Onboarding on topic {@code merchant.activated}.
 * S13 uses this to seed built-in roles and create the first ADMIN user.
 * Accepts both camelCase (S11 native) and snake_case field names.
 */
public record MerchantActivatedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        String companyName,
        String primaryContactEmail,
        String primaryContactName,
        Instant activatedAt
) {}
