package com.stablecoin.payments.merchant.onboarding.fixtures;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.ApprovedCorridorEntity;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantEntity;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantEntity.AddressJson;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MerchantEntityFixtures {

    private MerchantEntityFixtures() {}

    public static MerchantEntity anAppliedMerchantEntity() {
        return MerchantEntity.builder()
                .merchantId(UUID.randomUUID())
                .legalName("Acme Payments Ltd")
                .tradingName("Acme Pay")
                .registrationNumber("REG-" + UUID.randomUUID().toString().substring(0, 8))
                .registrationCountry("GB")
                .entityType(EntityType.PRIVATE_LIMITED)
                .websiteUrl("https://acmepay.com")
                .primaryCurrency("USD")
                .primaryContactEmail("admin@acmepay.com")
                .primaryContactName("Jane Smith")
                .status(MerchantStatus.APPLIED)
                .kybStatus(KybStatus.NOT_STARTED)
                .rateLimitTier(RateLimitTier.STARTER)
                .registeredAddress(anAddressJson())
                .beneficialOwners(List.of())
                .requestedCorridors(List.of("GB->US"))
                .allowedScopes(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static MerchantEntity anActiveMerchantEntity() {
        return MerchantEntity.builder()
                .merchantId(UUID.randomUUID())
                .legalName("Active Corp Ltd")
                .tradingName("ActiveCorp")
                .registrationNumber("REG-" + UUID.randomUUID().toString().substring(0, 8))
                .registrationCountry("GB")
                .entityType(EntityType.PRIVATE_LIMITED)
                .websiteUrl("https://activecorp.com")
                .primaryCurrency("USD")
                .primaryContactEmail("admin@activecorp.com")
                .primaryContactName("John Active")
                .status(MerchantStatus.ACTIVE)
                .kybStatus(KybStatus.PASSED)
                .riskTier(RiskTier.LOW)
                .rateLimitTier(RateLimitTier.GROWTH)
                .onboardedBy(MerchantFixtures.anApprover())
                .registeredAddress(anAddressJson())
                .beneficialOwners(List.of())
                .requestedCorridors(List.of("GB->US"))
                .allowedScopes(List.of("payments:read", "payments:write"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .activatedAt(Instant.now())
                .build();
    }

    public static ApprovedCorridorEntity anApprovedCorridorEntity(UUID merchantId) {
        return ApprovedCorridorEntity.builder()
                .corridorId(UUID.randomUUID())
                .merchantId(merchantId)
                .sourceCountry("GB")
                .targetCountry("US")
                .currencies(List.of("GBP", "USD"))
                .maxAmountUsd(new BigDecimal("100000.00"))
                .approvedBy(MerchantFixtures.anApprover())
                .approvedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(86400))
                .isActive(true)
                .build();
    }

    public static AddressJson anAddressJson() {
        return new AddressJson(
                "123 High Street", null, "London", null, "EC1A 1BB", "GB");
    }
}
