package com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record MerchantActivatedEvent(
        String eventId,
        String eventType,
        UUID merchantId,
        String correlationId,
        String legalName,
        String companyName,
        String primaryContactEmail,
        String primaryContactName,
        String country,
        List<String> scopes,
        String riskTier,
        String rateLimitTier,
        List<String> allowedScopes,
        String primaryCurrency,
        Instant activatedAt
) {
    public static final String TOPIC = "merchant.activated";
    public static final String EVENT_TYPE = "merchant.activated";
}
