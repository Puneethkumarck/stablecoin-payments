package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.exceptions.InvalidMerchantStateException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import com.stablecoin.payments.merchant.onboarding.fixtures.MerchantFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MerchantActivationPolicy")
class MerchantActivationPolicyTest {

    private final MerchantActivationPolicy policy = new MerchantActivationPolicy();

    @Nested
    @DisplayName("When KYB passed")
    class WhenKybPassed {

        @Test
        @DisplayName("should pass validation for PENDING_APPROVAL with LOW risk")
        void shouldPassValidationForLowRisk() {
            var merchant = MerchantFixtures.pendingApprovalMerchant();

            assertThatCode(() -> policy.validate(merchant))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should pass validation for PENDING_APPROVAL with MEDIUM risk")
        void shouldPassValidationForMediumRisk() {
            var merchant = MerchantFixtures.kybInProgressMerchant();
            merchant.kybPassed(RiskTier.MEDIUM);

            assertThatCode(() -> policy.validate(merchant))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("When KYB not passed")
    class WhenKybNotPassed {

        @Test
        @DisplayName("should reject APPLIED merchant")
        void shouldRejectAppliedMerchant() {
            var merchant = MerchantFixtures.appliedMerchant();

            assertThatThrownBy(() -> policy.validate(merchant))
                    .isInstanceOf(InvalidMerchantStateException.class);
        }

        @Test
        @DisplayName("should reject KYB_IN_PROGRESS merchant")
        void shouldRejectKybInProgressMerchant() {
            var merchant = MerchantFixtures.kybInProgressMerchant();

            assertThatThrownBy(() -> policy.validate(merchant))
                    .isInstanceOf(InvalidMerchantStateException.class);
        }
    }

    @Nested
    @DisplayName("When HIGH risk")
    class WhenHighRisk {

        @Test
        @DisplayName("should reject HIGH risk merchant even with KYB passed")
        void shouldRejectHighRiskMerchant() {
            var merchant = MerchantFixtures.kybInProgressMerchant();
            merchant.kybPassed(RiskTier.HIGH);

            assertThatThrownBy(() -> policy.validate(merchant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HIGH");
        }
    }
}
