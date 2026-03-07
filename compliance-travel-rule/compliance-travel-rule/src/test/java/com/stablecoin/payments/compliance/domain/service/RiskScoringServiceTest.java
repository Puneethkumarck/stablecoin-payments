package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.RiskScoringWeights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.stablecoin.payments.compliance.domain.model.RiskBand.CRITICAL;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.HIGH;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.LOW;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.MEDIUM;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aCheckInRiskScoringStatus;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aRiskScoringContext;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskScoringService")
class RiskScoringServiceTest {

    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        service = new RiskScoringService(RiskScoringWeights.defaults());
    }

    @Nested
    @DisplayName("Score calculation with default weights")
    class DefaultWeights {

        @Test
        @DisplayName("should return base score 0 with no risk factors for established customer")
        void should_returnZero_when_noRiskFactors() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var profile = aRiskProfile();
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(0).band(LOW).factors(List.of()).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should add 20 penalty for KYC tier 1")
        void should_add20Penalty_when_kycTier1() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("500"), "USD", "US", "US", false);
            var profile = aRiskProfile();
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(20).band(LOW)
                    .factors(List.of("kyc_tier_1_sender")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add KYC penalty for tier 2")
        void should_notAddKycPenalty_when_kycTier2() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.factors()).doesNotContain("kyc_tier_1_sender");
        }

        @Test
        @DisplayName("should add 15 penalty for high-value transaction (>= $10000)")
        void should_add15Penalty_when_highValueTransaction() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("10000"), "USD", "US", "US", false);
            var profile = aRiskProfile();
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(25).band(LOW)
                    .factors(List.of("high_value_transaction", "amount_near_limit")).build();
            assertThat(result).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add high-value penalty for amount below threshold")
        void should_notAddHighValuePenalty_when_belowThreshold() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("9999.99"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.factors()).doesNotContain("high_value_transaction");
        }

        @Test
        @DisplayName("should add 30 penalty when AML is flagged")
        void should_add30Penalty_when_amlFlagged() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", true);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(30).band(MEDIUM)
                    .factors(List.of("aml_flagged")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should add 10 penalty for cross-border transaction")
        void should_add10Penalty_when_crossBorder() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "DE", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(10).band(LOW)
                    .factors(List.of("cross_border")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add cross-border penalty when same country")
        void should_notAddCrossBorderPenalty_when_sameCountry() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.factors()).doesNotContain("cross_border");
        }

        @Test
        @DisplayName("should add 15 penalty for new customer (no risk profile)")
        void should_add15Penalty_when_newCustomer() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, null, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(15).band(LOW)
                    .factors(List.of("new_customer")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should add 10 penalty when amount near per-txn limit (>= 80%)")
        void should_add10Penalty_when_amountNearLimit() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("8000"), "USD", "US", "US", false);
            var profile = aRiskProfile(); // perTxnLimitUsd = 10000
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(10).band(LOW)
                    .factors(List.of("amount_near_limit")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add amount-near-limit penalty when below 80%")
        void should_notAddAmountNearLimit_when_belowThreshold() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("7999"), "USD", "US", "US", false);
            var profile = aRiskProfile(); // perTxnLimitUsd = 10000
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.factors()).doesNotContain("amount_near_limit");
        }

        @Test
        @DisplayName("should add 15 penalty for high transaction velocity (>= 10)")
        void should_add15Penalty_when_highVelocity() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 10);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder().score(15).band(LOW)
                    .factors(List.of("high_velocity")).build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add velocity penalty when below threshold")
        void should_notAddVelocityPenalty_when_belowThreshold() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 9);

            RiskScore result = service.calculateScore(context);

            assertThat(result.factors()).doesNotContain("high_velocity");
        }

        @Test
        @DisplayName("should accumulate multiple risk factors")
        void should_accumulateFactors_when_multipleRisksPresent() {
            // KYC tier 1 (20) + high-value (15) + cross-border (10) + amount_near_limit (10) = 55
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("15000"), "USD", "US", "DE", false);
            var profile = aRiskProfile(); // perTxnLimitUsd = 10000 => 15000/10000 = 1.5 >= 0.8
            var context = aRiskScoringContext(check, profile, 0);

            RiskScore result = service.calculateScore(context);

            var expected = RiskScore.builder()
                    .score(55)
                    .band(HIGH)
                    .factors(List.of("kyc_tier_1_sender", "high_value_transaction", "cross_border", "amount_near_limit"))
                    .build();
            assertThat(result).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        @DisplayName("should reach CRITICAL band when all factors combine")
        void should_reachCritical_when_allFactorsCombine() {
            // KYC1 (20) + high-value (15) + AML (30) + cross-border (10) + new customer (15) + velocity (15) = 105 -> capped at 100
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("50000"), "USD", "US", "DE", true);
            var context = aRiskScoringContext(check, null, 15);

            RiskScore result = service.calculateScore(context);

            assertThat(result.score()).isEqualTo(100);
            assertThat(result.band()).isEqualTo(CRITICAL);
        }

        @Test
        @DisplayName("should cap score at 100 when factors exceed max")
        void should_capAt100_when_factorsExceedMax() {
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("50000"), "USD", "US", "DE", true);
            var context = aRiskScoringContext(check, null, 20);

            RiskScore result = service.calculateScore(context);

            assertThat(result.score()).isLessThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Configurable weights")
    class ConfigurableWeights {

        @Test
        @DisplayName("should use custom weights from configuration")
        void should_useCustomWeights_when_configured() {
            var customWeights = RiskScoringWeights.builder()
                    .kycTier1Penalty(5)
                    .highValuePenalty(5)
                    .amlFlagPenalty(10)
                    .crossBorderPenalty(5)
                    .newCustomerPenalty(5)
                    .highCorridorRiskPenalty(10)
                    .amountToLimitRatioPenalty(5)
                    .highVelocityPenalty(5)
                    .corridorRiskScores(Map.of())
                    .build();
            var customService = new RiskScoringService(customWeights);

            // KYC1 (5) + cross-border (5) = 10
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("500"), "USD", "US", "DE", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = customService.calculateScore(context);

            var expected = RiskScore.builder().score(10).band(LOW)
                    .factors(List.of("kyc_tier_1_sender", "cross_border")).build();
            assertThat(result).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Corridor risk scoring")
    class CorridorRisk {

        @Test
        @DisplayName("should add corridor-specific risk score for high-risk corridor")
        void should_addCorridorRisk_when_highRiskCorridor() {
            var weights = RiskScoringWeights.defaults().toBuilder()
                    .corridorRiskScores(Map.of("US-IR", 25, "US-NG", 15))
                    .build();
            var corridorService = new RiskScoringService(weights);

            // cross-border (10) + corridor risk (25) = 35
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "IR", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = corridorService.calculateScore(context);

            var expected = RiskScore.builder().score(35).band(MEDIUM)
                    .factors(List.of("cross_border", "high_risk_corridor")).build();
            assertThat(result).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add corridor risk for unconfigured corridor")
        void should_notAddCorridorRisk_when_corridorNotConfigured() {
            var weights = RiskScoringWeights.defaults().toBuilder()
                    .corridorRiskScores(Map.of("US-IR", 25))
                    .build();
            var corridorService = new RiskScoringService(weights);

            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "DE", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = corridorService.calculateScore(context);

            assertThat(result.factors()).doesNotContain("high_risk_corridor");
        }
    }

    @Nested
    @DisplayName("Risk bands")
    class RiskBands {

        @Test
        @DisplayName("should assign LOW band for score 0-25")
        void should_assignLow_when_scoreIsLow() {
            // No risk factors for established customer, same country
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.band()).isEqualTo(LOW);
        }

        @Test
        @DisplayName("should assign MEDIUM band for score 26-50")
        void should_assignMedium_when_scoreIsMedium() {
            // AML flagged (30) = MEDIUM
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", true);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.band()).isEqualTo(MEDIUM);
        }

        @Test
        @DisplayName("should assign HIGH band for score 51-75")
        void should_assignHigh_when_scoreIsHigh() {
            // AML (30) + KYC1 (20) + cross-border (10) = 60
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("500"), "USD", "US", "DE", true);
            var context = aRiskScoringContext(check, aRiskProfile(), 0);

            RiskScore result = service.calculateScore(context);

            assertThat(result.band()).isEqualTo(HIGH);
        }

        @Test
        @DisplayName("should assign CRITICAL band for score 76-100")
        void should_assignCritical_when_scoreIsCritical() {
            // KYC1 (20) + AML (30) + cross-border (10) + new_customer (15) + velocity (15) = 90
            var check = aCheckInRiskScoringStatus(
                    KycTier.KYC_TIER_1, new BigDecimal("500"), "USD", "US", "DE", true);
            var context = aRiskScoringContext(check, null, 12);

            RiskScore result = service.calculateScore(context);

            assertThat(result.band()).isEqualTo(CRITICAL);
        }
    }
}
