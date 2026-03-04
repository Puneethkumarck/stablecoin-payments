package com.stablecoin.payments.merchant.onboarding.domain.merchant.model.command;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BeneficialOwner;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BusinessAddress;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;

import java.util.List;

public record ApplyMerchantCommand(
        String legalName,
        String tradingName,
        String registrationNumber,
        String registrationCountry,
        EntityType entityType,
        String websiteUrl,
        String primaryCurrency,
        BusinessAddress registeredAddress,
        List<BeneficialOwner> beneficialOwners,
        List<String> requestedCorridors
) {}
