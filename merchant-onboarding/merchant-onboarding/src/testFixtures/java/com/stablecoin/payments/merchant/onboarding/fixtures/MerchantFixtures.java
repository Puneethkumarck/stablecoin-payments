package com.stablecoin.payments.merchant.onboarding.fixtures;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.Merchant;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BeneficialOwner;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BusinessAddress;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MerchantFixtures {

    private MerchantFixtures() {}

    public static Merchant aNewMerchant() {
        return Merchant.createNew(
                "Acme Payments Ltd",
                "Acme Pay",
                "REG-12345",
                "GB",
                EntityType.PRIVATE_LIMITED,
                "https://acmepay.com",
                "USD",
                "admin@acmepay.com",
                "Jane Smith",
                aRegisteredAddress(),
                aBeneficialOwners(),
                List.of("GB->US", "GB->EU")
        );
    }

    public static Merchant appliedMerchant() {
        return aNewMerchant();
    }

    public static Merchant kybInProgressMerchant() {
        var merchant = aNewMerchant();
        merchant.startKyb();
        return merchant;
    }

    public static Merchant pendingApprovalMerchant() {
        var merchant = kybInProgressMerchant();
        merchant.kybPassed(RiskTier.LOW);
        return merchant;
    }

    public static Merchant activeMerchant() {
        var merchant = pendingApprovalMerchant();
        merchant.activate(anApprover(), List.of("payments:read", "payments:write"));
        return merchant;
    }

    public static Merchant suspendedMerchant() {
        var merchant = activeMerchant();
        merchant.suspend();
        return merchant;
    }

    public static Merchant closedMerchant() {
        var merchant = activeMerchant();
        merchant.close();
        return merchant;
    }

    public static BusinessAddress aRegisteredAddress() {
        return BusinessAddress.builder()
                .streetLine1("123 High Street")
                .city("London")
                .postcode("EC1A 1BB")
                .country("GB")
                .build();
    }

    public static List<BeneficialOwner> aBeneficialOwners() {
        return List.of(BeneficialOwner.builder()
                .fullName("Jane Smith")
                .dateOfBirth(LocalDate.of(1980, 5, 15))
                .nationality("GB")
                .ownershipPct(new BigDecimal("100.00"))
                .isPoliticallyExposed(false)
                .build());
    }

    public static UUID anApprover() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
