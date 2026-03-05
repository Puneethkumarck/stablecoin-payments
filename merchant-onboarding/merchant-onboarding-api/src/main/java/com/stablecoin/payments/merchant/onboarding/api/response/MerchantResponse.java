package com.stablecoin.payments.merchant.onboarding.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MerchantResponse(
        UUID merchantId,
        String legalName,
        String tradingName,
        String registrationNumber,
        String registrationCountry,
        String entityType,
        String websiteUrl,
        String primaryCurrency,
        String primaryContactEmail,
        String primaryContactName,
        String status,
        String kybStatus,
        String riskTier,
        String rateLimitTier,
        List<String> allowedScopes,
        List<String> requestedCorridors,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt
) {}
