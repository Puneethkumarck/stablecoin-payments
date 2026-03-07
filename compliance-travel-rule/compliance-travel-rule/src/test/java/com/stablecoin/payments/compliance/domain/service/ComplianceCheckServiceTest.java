package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.model.TransmissionStatus;
import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;
import com.stablecoin.payments.compliance.domain.model.TravelRuleProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.SANCTIONS_HIT;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComplianceCheckService")
class ComplianceCheckServiceTest {

    private ComplianceCheckService service;

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final Money SOURCE_AMOUNT = new Money(new BigDecimal("5000.00"), "USD");
    private static final String SOURCE_COUNTRY = "US";
    private static final String TARGET_COUNTRY = "DE";
    private static final String TARGET_CURRENCY = "EUR";

    @BeforeEach
    void setUp() {
        service = new ComplianceCheckService();
    }

    private ComplianceCheck createPendingCheck() {
        return service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                SOURCE_AMOUNT, SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);
    }

    private static KycResult verifiedKycResult() {
        return KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderKycTier(KycTier.KYC_TIER_2)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("onfido")
                .checkedAt(Instant.now())
                .build();
    }

    private static KycResult senderRejectedKycResult() {
        return KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderKycTier(KycTier.KYC_TIER_1)
                .senderStatus(KycStatus.REJECTED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("onfido")
                .checkedAt(Instant.now())
                .build();
    }

    private static KycResult recipientRejectedKycResult() {
        return KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderKycTier(KycTier.KYC_TIER_2)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.REJECTED)
                .provider("onfido")
                .checkedAt(Instant.now())
                .build();
    }

    private static SanctionsResult clearSanctionsResult() {
        return SanctionsResult.builder()
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
    }

    private static SanctionsResult senderHitSanctionsResult() {
        return SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(true)
                .recipientHit(false)
                .hitDetails("SDN list match")
                .listsChecked(List.of("OFAC"))
                .provider("chainalysis")
                .screenedAt(Instant.now())
                .build();
    }

    private static AmlResult clearAmlResult() {
        return AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .flagged(false)
                .flagReasons(List.of())
                .provider("chainalysis")
                .screenedAt(Instant.now())
                .build();
    }

    private static AmlResult flaggedAmlResult() {
        return AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .flagged(true)
                .flagReasons(List.of("high_risk_jurisdiction"))
                .provider("chainalysis")
                .screenedAt(Instant.now())
                .build();
    }

    private static RiskScore lowRiskScore() {
        return RiskScore.builder()
                .score(10)
                .band(RiskBand.LOW)
                .factors(List.of())
                .build();
    }

    private static TravelRulePackage travelRulePackage() {
        return TravelRulePackage.builder()
                .packageId(UUID.randomUUID())
                .checkId(UUID.randomUUID())
                .protocol(TravelRuleProtocol.IVMS101)
                .transmissionStatus(TransmissionStatus.TRANSMITTED)
                .transmittedAt(Instant.now())
                .build();
    }

    // ── initiate() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("initiate()")
    class Initiate {

        @Test
        @DisplayName("should create check in PENDING status")
        void should_createCheckInPending_when_initiated() {
            var check = createPendingCheck();

            var expected = ComplianceCheck.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);
            assertThat(check)
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "createdAt", "expiresAt")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    // ── recordKycResult() ────────────────────────────────────────────

    @Nested
    @DisplayName("recordKycResult()")
    class RecordKycResult {

        @Test
        @DisplayName("should transition to SANCTIONS_SCREENING when KYC passes")
        void should_transitionToSanctionsScreening_when_kycPasses() {
            var check = createPendingCheck();
            var kycResult = verifiedKycResult();

            var result = service.recordKycResult(check, kycResult);

            var expected = check.startKyc().passKyc(kycResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition to FAILED when sender KYC is REJECTED")
        void should_fail_when_senderKycRejected() {
            var check = createPendingCheck();
            var kycResult = senderRejectedKycResult();

            var result = service.recordKycResult(check, kycResult);

            var expected = check.startKyc().failKyc(kycResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition to FAILED when recipient KYC is REJECTED")
        void should_fail_when_recipientKycRejected() {
            var check = createPendingCheck();
            var kycResult = recipientRejectedKycResult();

            var result = service.recordKycResult(check, kycResult);

            var expected = check.startKyc().failKyc(kycResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }
    }

    // ── recordSanctionsResult() ──────────────────────────────────────

    @Nested
    @DisplayName("recordSanctionsResult()")
    class RecordSanctionsResult {

        @Test
        @DisplayName("should transition to AML_SCREENING when sanctions clear")
        void should_transitionToAmlScreening_when_sanctionsClear() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            var sanctionsResult = clearSanctionsResult();

            var result = service.recordSanctionsResult(check, sanctionsResult);

            var expected = check.sanctionsClear(sanctionsResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition to SANCTIONS_HIT when sender hit detected")
        void should_sanctionsHit_when_senderHitDetected() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            var sanctionsResult = senderHitSanctionsResult();

            var result = service.recordSanctionsResult(check, sanctionsResult);

            var expected = check.sanctionsHitDetected(sanctionsResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition to SANCTIONS_HIT when recipient hit detected")
        void should_sanctionsHit_when_recipientHitDetected() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());

            var recipientHit = SanctionsResult.builder()
                    .sanctionsResultId(UUID.randomUUID())
                    .checkId(UUID.randomUUID())
                    .senderScreened(true)
                    .recipientScreened(true)
                    .senderHit(false)
                    .recipientHit(true)
                    .hitDetails("EU sanctions match")
                    .listsChecked(List.of("EU"))
                    .provider("chainalysis")
                    .screenedAt(Instant.now())
                    .build();

            var result = service.recordSanctionsResult(check, recipientHit);

            assertThat(result.status()).isEqualTo(SANCTIONS_HIT);
        }
    }

    // ── recordAmlResult() ────────────────────────────────────────────

    @Nested
    @DisplayName("recordAmlResult()")
    class RecordAmlResult {

        @Test
        @DisplayName("should transition to RISK_SCORING when AML clear")
        void should_transitionToRiskScoring_when_amlClear() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            check = service.recordSanctionsResult(check, clearSanctionsResult());
            var amlResult = clearAmlResult();

            var result = service.recordAmlResult(check, amlResult);

            var expected = check.amlClear(amlResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition to MANUAL_REVIEW when AML flagged")
        void should_manualReview_when_amlFlagged() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            check = service.recordSanctionsResult(check, clearSanctionsResult());
            var amlResult = flaggedAmlResult();

            var result = service.recordAmlResult(check, amlResult);

            var expected = check.amlFlagged(amlResult);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }
    }

    // ── recordRiskScore() ────────────────────────────────────────────

    @Nested
    @DisplayName("recordRiskScore()")
    class RecordRiskScore {

        @Test
        @DisplayName("should transition to TRAVEL_RULE_PACKAGING")
        void should_transitionToTravelRulePackaging_when_riskScored() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            check = service.recordSanctionsResult(check, clearSanctionsResult());
            check = service.recordAmlResult(check, clearAmlResult());
            var riskScore = lowRiskScore();

            var result = service.recordRiskScore(check, riskScore);

            var expected = check.riskScored(riskScore);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    // ── recordTravelRuleResult() ─────────────────────────────────────

    @Nested
    @DisplayName("recordTravelRuleResult()")
    class RecordTravelRuleResult {

        @Test
        @DisplayName("should transition to PASSED")
        void should_transitionToPassed_when_travelRuleComplete() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            check = service.recordSanctionsResult(check, clearSanctionsResult());
            check = service.recordAmlResult(check, clearAmlResult());
            check = service.recordRiskScore(check, lowRiskScore());
            var travelRule = travelRulePackage();

            var result = service.recordTravelRuleResult(check, travelRule);

            var expected = check.completeTravelRule(travelRule);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }
    }

    // ── skipTravelRule() ─────────────────────────────────────────────

    @Nested
    @DisplayName("skipTravelRule()")
    class SkipTravelRule {

        @Test
        @DisplayName("should transition to PASSED with null travel rule package")
        void should_transitionToPassed_when_travelRuleSkipped() {
            var check = createPendingCheck();
            check = service.recordKycResult(check, verifiedKycResult());
            check = service.recordSanctionsResult(check, clearSanctionsResult());
            check = service.recordAmlResult(check, clearAmlResult());
            check = service.recordRiskScore(check, lowRiskScore());

            var result = service.skipTravelRule(check);

            var expected = check.completeTravelRule(null);
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }
    }

    // ── requiresTravelRule() ─────────────────────────────────────────

    @Nested
    @DisplayName("requiresTravelRule()")
    class RequiresTravelRule {

        @Test
        @DisplayName("should return false for USD $999")
        void should_returnFalse_when_usdBelow1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("999"), "USD"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isFalse();
        }

        @Test
        @DisplayName("should return true for USD $1000")
        void should_returnTrue_when_usdEquals1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("1000"), "USD"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isTrue();
        }

        @Test
        @DisplayName("should return true for USD $1001")
        void should_returnTrue_when_usdAbove1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("1001"), "USD"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isTrue();
        }

        @Test
        @DisplayName("should return false for EUR 999")
        void should_returnFalse_when_eurBelow1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("999"), "EUR"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isFalse();
        }

        @Test
        @DisplayName("should return true for EUR 1000")
        void should_returnTrue_when_eurEquals1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("1000"), "EUR"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isTrue();
        }

        @Test
        @DisplayName("should return true for GBP any amount (unknown currency defaults to required)")
        void should_returnTrue_when_unknownCurrency() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("100"), "GBP"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isTrue();
        }

        @Test
        @DisplayName("should return false for USD $999.99")
        void should_returnFalse_when_usdJustBelow1000() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("999.99"), "USD"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isFalse();
        }

        @Test
        @DisplayName("should return true for GBP $500 (non-USD/EUR always requires)")
        void should_returnTrue_when_gbpAnyAmount() {
            var check = service.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                    new Money(new BigDecimal("500"), "GBP"),
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);

            assertThat(service.requiresTravelRule(check)).isTrue();
        }
    }
}
