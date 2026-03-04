package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.exceptions.InvalidMerchantStateException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import org.springframework.stereotype.Service;

/**
 * Domain service: enforces all invariants before a merchant can be activated.
 */
@Service
public class MerchantActivationPolicy {

    public void validate(Merchant merchant) {
        if (merchant.getStatus() != MerchantStatus.PENDING_APPROVAL) {
            throw InvalidMerchantStateException.forMerchant(
                    merchant.getMerchantId(), merchant.getStatus(), "activate");
        }
        if (merchant.getKybStatus() != KybStatus.PASSED) {
            throw new IllegalStateException(
                    "Cannot activate merchant %s: KYB status is %s, expected PASSED"
                            .formatted(merchant.getMerchantId(), merchant.getKybStatus()));
        }
        if (merchant.getRiskTier() == RiskTier.HIGH) {
            throw new IllegalStateException(
                    "Cannot activate merchant %s: risk tier is HIGH"
                            .formatted(merchant.getMerchantId()));
        }
    }
}
