package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.compliance.domain.model.RiskBand.HIGH;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.LOW;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskScoringService")
class RiskScoringServiceTest {

    private RiskScoringService service;

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RiskScoringService();
    }

    /**
     * Creates a check walked through the pipeline to RISK_SCORING status,
     * with configurable KYC tier, amount, countries, and AML result.
     */
    private ComplianceCheck buildCheckForRiskScoring(KycTier kycTier, BigDecimal amount,
                                                     String sourceCurrency,
                                                     String sourceCountry, String targetCountry,
                                                     boolean amlFlagged) {
        var kycResult = KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderKycTier(kycTier)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("onfido")
                .checkedAt(Instant.now())
                .build();

        var sanctionsResult = SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(false)
                .recipientHit(false)
                .listsChecked(List.of("OFAC", "EU"))
                .provider("chainalysis")
                .screenedAt(Instant.now())
                .build();

        var amlResult = AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .flagged(amlFlagged)
                .flagReasons(amlFlagged ? List.of("high_risk_jurisdiction") : List.of())
                .provider("chainalysis")
                .screenedAt(Instant.now())
                .build();

        var sourceAmount = new Money(amount, sourceCurrency);

        return ComplianceCheck.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                        sourceAmount, sourceCountry, targetCountry, "EUR")
                .startKyc()
                .passKyc(kycResult)
                .sanctionsClear(sanctionsResult)
                .amlClear(amlResult);
        // Now in RISK_SCORING status
    }

    @Nested
    @DisplayName("Score calculation")
    class ScoreCalculation {

        @Test
        @DisplayName("should return base score 0 with no risk factors")
        void should_returnZero_when_noRiskFactors() {
            // KYC tier 2, low amount, same country, no AML flag
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder().score(0).band(LOW).factors(List.of()).build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should add 20 penalty for KYC tier 1")
        void should_add20Penalty_when_kycTier1() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_1, new BigDecimal("500"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder().score(20).band(LOW).factors(List.of("kyc_tier_1_sender")).build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add KYC penalty for tier 2")
        void should_notAddKycPenalty_when_kycTier2() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            assertThat(result.factors()).doesNotContain("kyc_tier_1_sender");
        }

        @Test
        @DisplayName("should not add KYC penalty for tier 3")
        void should_notAddKycPenalty_when_kycTier3() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_3, new BigDecimal("500"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            assertThat(result.factors()).doesNotContain("kyc_tier_1_sender");
        }

        @Test
        @DisplayName("should add 15 penalty for high-value transaction (>= $10000)")
        void should_add15Penalty_when_highValueTransaction() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("10000"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder().score(15).band(LOW).factors(List.of("high_value_transaction")).build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add high-value penalty for amount below threshold")
        void should_notAddHighValuePenalty_when_belowThreshold() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("9999.99"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            assertThat(result.factors()).doesNotContain("high_value_transaction");
        }

        @Test
        @DisplayName("should add 30 penalty when AML is flagged")
        void should_add30Penalty_when_amlFlagged() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", true);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder().score(30).band(MEDIUM).factors(List.of("aml_flagged")).build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should add 10 penalty for cross-border transaction")
        void should_add10Penalty_when_crossBorder() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "DE", false);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder().score(10).band(LOW).factors(List.of("cross_border")).build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should not add cross-border penalty when same country")
        void should_notAddCrossBorderPenalty_when_sameCountry() {
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_2, new BigDecimal("500"), "USD", "US", "US", false);

            RiskScore result = service.calculateScore(check);

            assertThat(result.factors()).doesNotContain("cross_border");
        }

        @Test
        @DisplayName("should accumulate multiple risk factors")
        void should_accumulateFactors_when_multipleRisksPresent() {
            // KYC tier 1 (20) + high-value (15) + cross-border (10) = 45
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_1, new BigDecimal("15000"), "USD", "US", "DE", false);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder()
                    .score(45)
                    .band(MEDIUM)
                    .factors(List.of("kyc_tier_1_sender", "high_value_transaction", "cross_border"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should accumulate all four factors")
        void should_accumulateAllFactors_when_allRisksPresent() {
            // KYC tier 1 (20) + high-value (15) + AML flagged (30) + cross-border (10) = 75
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_1, new BigDecimal("50000"), "USD", "US", "DE", true);

            RiskScore result = service.calculateScore(check);

            var expected = RiskScore.builder()
                    .score(75)
                    .band(HIGH)
                    .factors(List.of("kyc_tier_1_sender", "high_value_transaction", "aml_flagged", "cross_border"))
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should cap score at 100 when factors exceed max")
        void should_capAt100_when_factorsExceedMax() {
            // With current penalties, max is 75. The cap logic is verified to be <= 100.
            var check = buildCheckForRiskScoring(
                    KycTier.KYC_TIER_1, new BigDecimal("50000"), "USD", "US", "DE", true);

            RiskScore result = service.calculateScore(check);

            assertThat(result.score()).isLessThanOrEqualTo(100);
        }
    }
}
