package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import org.springframework.stereotype.Service;

/**
 * Domain service: validates corridor approval against merchant status and regulatory rules.
 */
@Service
public class CorridorEntitlementService {

    public void validate(Merchant merchant, String sourceCountry, String targetCountry) {
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Corridor approval requires ACTIVE merchant, got: %s"
                            .formatted(merchant.getStatus()));
        }
        if (sourceCountry.equals(targetCountry)) {
            throw new IllegalArgumentException(
                    "Source and target country must be different: %s".formatted(sourceCountry));
        }
    }
}
