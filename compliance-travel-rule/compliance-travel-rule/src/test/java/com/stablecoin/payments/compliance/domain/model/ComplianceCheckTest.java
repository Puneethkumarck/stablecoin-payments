package com.stablecoin.payments.compliance.domain.model;

import com.stablecoin.payments.compliance.domain.statemachine.StateMachineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.FAILED;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.KYC_IN_PROGRESS;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.MANUAL_REVIEW;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.PASSED;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.PENDING;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.RISK_SCORING;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.SANCTIONS_HIT;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus.SANCTIONS_SCREENING;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.AML_CLEAR;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.ESCALATE_MANUAL_REVIEW;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.KYC_FAILED;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.KYC_PASSED;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.RISK_SCORED;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.SANCTIONS_CLEAR;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.START_KYC;
import static com.stablecoin.payments.compliance.domain.model.ComplianceCheckTrigger.TRAVEL_RULE_COMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ComplianceCheck aggregate root")
class ComplianceCheckTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final Money SOURCE_AMOUNT = new Money(new BigDecimal("5000.00"), "USD");
    private static final String SOURCE_COUNTRY = "US";
    private static final String TARGET_COUNTRY = "DE";
    private static final String TARGET_CURRENCY = "EUR";

    // ── Test fixtures ────────────────────────────────────────────────

    private static ComplianceCheck createPendingCheck() {
        return ComplianceCheck.initiate(PAYMENT_ID, SENDER_ID, RECIPIENT_ID,
                SOURCE_AMOUNT, SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY);
    }

    private static KycResult passingKycResult() {
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

    private static KycResult failingKycResult() {
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

    private static SanctionsResult hitSanctionsResult() {
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
                .score(15)
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

    /**
     * Walks the check through the full happy path up to TRAVEL_RULE_PACKAGING status.
     */
    private static ComplianceCheck walkToTravelRulePackaging() {
        return createPendingCheck()
                .startKyc()
                .passKyc(passingKycResult())
                .sanctionsClear(clearSanctionsResult())
                .amlClear(clearAmlResult())
                .riskScored(lowRiskScore());
    }

    // ── Factory Method Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("initiate() factory")
    class InitiateFactory {

        @Test
        @DisplayName("should create check in PENDING status with all fields populated")
        void should_createCheckInPendingStatus_when_validInputsProvided() {
            var check = createPendingCheck();

            var expected = ComplianceCheck.builder()
                    .paymentId(PAYMENT_ID)
                    .senderId(SENDER_ID)
                    .recipientId(RECIPIENT_ID)
                    .sourceAmount(new BigDecimal("5000.00"))
                    .sourceCurrency("USD")
                    .targetCurrency(TARGET_CURRENCY)
                    .sourceCountry(SOURCE_COUNTRY)
                    .targetCountry(TARGET_COUNTRY)
                    .status(PENDING)
                    .build();
            assertThat(check)
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "createdAt", "expiresAt")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when paymentId is null")
        void should_throwIllegalArgument_when_paymentIdIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    null, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("paymentId");
        }

        @Test
        @DisplayName("should throw when senderId is null")
        void should_throwIllegalArgument_when_senderIdIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, null, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("senderId");
        }

        @Test
        @DisplayName("should throw when recipientId is null")
        void should_throwIllegalArgument_when_recipientIdIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, null, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recipientId");
        }

        @Test
        @DisplayName("should throw when sourceAmount is null")
        void should_throwIllegalArgument_when_sourceAmountIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, null,
                    SOURCE_COUNTRY, TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceAmount");
        }

        @Test
        @DisplayName("should throw when sourceCountry is null")
        void should_throwIllegalArgument_when_sourceCountryIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    null, TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCountry");
        }

        @Test
        @DisplayName("should throw when sourceCountry is blank")
        void should_throwIllegalArgument_when_sourceCountryIsBlank() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    "  ", TARGET_COUNTRY, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceCountry");
        }

        @Test
        @DisplayName("should throw when targetCountry is null")
        void should_throwIllegalArgument_when_targetCountryIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, null, TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCountry");
        }

        @Test
        @DisplayName("should throw when targetCountry is blank")
        void should_throwIllegalArgument_when_targetCountryIsBlank() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, "", TARGET_CURRENCY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCountry");
        }

        @Test
        @DisplayName("should throw when targetCurrency is null")
        void should_throwIllegalArgument_when_targetCurrencyIsNull() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, TARGET_COUNTRY, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCurrency");
        }

        @Test
        @DisplayName("should throw when targetCurrency is blank")
        void should_throwIllegalArgument_when_targetCurrencyIsBlank() {
            assertThatThrownBy(() -> ComplianceCheck.initiate(
                    PAYMENT_ID, SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT,
                    SOURCE_COUNTRY, TARGET_COUNTRY, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCurrency");
        }
    }

    // ── Happy Path Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path transitions")
    class HappyPath {

        @Test
        @DisplayName("should walk through full pipeline: PENDING -> ... -> PASSED")
        void should_reachPassed_when_allSubChecksClear() {
            var kycResult = passingKycResult();
            var sanctionsResult = clearSanctionsResult();
            var amlResult = clearAmlResult();
            var riskScore = lowRiskScore();
            var travelRule = travelRulePackage();

            var result = createPendingCheck()
                    .startKyc()
                    .passKyc(kycResult)
                    .sanctionsClear(sanctionsResult)
                    .amlClear(amlResult)
                    .riskScored(riskScore)
                    .completeTravelRule(travelRule);

            var expected = ComplianceCheck.builder()
                    .paymentId(PAYMENT_ID)
                    .senderId(SENDER_ID)
                    .recipientId(RECIPIENT_ID)
                    .sourceAmount(new BigDecimal("5000.00"))
                    .sourceCurrency("USD")
                    .targetCurrency(TARGET_CURRENCY)
                    .sourceCountry(SOURCE_COUNTRY)
                    .targetCountry(TARGET_COUNTRY)
                    .status(PASSED)
                    .overallResult(OverallResult.PASSED)
                    .kycResult(kycResult)
                    .sanctionsResult(sanctionsResult)
                    .amlResult(amlResult)
                    .riskScore(riskScore)
                    .travelRulePackage(travelRule)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "createdAt", "completedAt", "expiresAt")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should allow completeTravelRule with null package (skip scenario)")
        void should_passTravelRule_when_packageIsNull() {
            var check = walkToTravelRulePackaging();
            var result = check.completeTravelRule(null);

            var expected = check.toBuilder()
                    .status(PASSED)
                    .overallResult(OverallResult.PASSED)
                    .travelRulePackage(null)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }
    }

    // ── Failure Path Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Failure path transitions")
    class FailurePaths {

        @Test
        @DisplayName("should transition KYC_IN_PROGRESS -> FAILED via failKyc")
        void should_fail_when_kycFails() {
            var check = createPendingCheck().startKyc();
            var kycResult = failingKycResult();

            var result = check.failKyc(kycResult);

            var expected = check.toBuilder()
                    .status(FAILED)
                    .overallResult(OverallResult.FAILED)
                    .errorCode("CO-1001")
                    .errorMessage("KYC verification failed")
                    .kycResult(kycResult)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition SANCTIONS_SCREENING -> SANCTIONS_HIT via sanctionsHitDetected")
        void should_sanctionsHit_when_hitDetected() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult());
            var hitResult = hitSanctionsResult();

            var result = check.sanctionsHitDetected(hitResult);

            var expected = check.toBuilder()
                    .status(SANCTIONS_HIT)
                    .overallResult(OverallResult.SANCTIONS_HIT)
                    .errorCode("CO-2001")
                    .errorMessage("Sanctions screening hit detected")
                    .sanctionsResult(hitResult)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition AML_SCREENING -> MANUAL_REVIEW via amlFlagged")
        void should_manualReview_when_amlFlagged() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult());
            var flaggedResult = flaggedAmlResult();

            var result = check.amlFlagged(flaggedResult);

            var expected = check.toBuilder()
                    .status(MANUAL_REVIEW)
                    .overallResult(OverallResult.MANUAL_REVIEW)
                    .errorCode("CO-3001")
                    .errorMessage("AML screening flagged \u2014 manual review required")
                    .amlResult(flaggedResult)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should transition TRAVEL_RULE_PACKAGING -> FAILED via failTravelRule")
        void should_fail_when_travelRuleFails() {
            var check = walkToTravelRulePackaging();

            var result = check.failTravelRule("Beneficiary VASP unreachable");

            var expected = check.toBuilder()
                    .status(FAILED)
                    .overallResult(OverallResult.FAILED)
                    .errorCode("CO-5001")
                    .errorMessage("Beneficiary VASP unreachable")
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("completedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should use default message when failTravelRule reason is null")
        void should_useDefaultMessage_when_failTravelRuleReasonIsNull() {
            var check = walkToTravelRulePackaging();
            var failed = check.failTravelRule(null);

            assertThat(failed.errorMessage()).isEqualTo("Travel rule transmission failed");
        }
    }

    // ── Escalation Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Escalation transitions")
    class Escalation {

        @Test
        @DisplayName("should transition SANCTIONS_HIT -> MANUAL_REVIEW via escalateToManualReview")
        void should_escalateToManualReview_when_sanctionsHit() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsHitDetected(hitSanctionsResult());

            var result = check.escalateToManualReview();

            var expected = check.toBuilder()
                    .status(MANUAL_REVIEW)
                    .overallResult(OverallResult.MANUAL_REVIEW)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    // ── Terminal State Immutability ──────────────────────────────────

    @Nested
    @DisplayName("Terminal state immutability")
    class TerminalStateImmutability {

        @Test
        @DisplayName("should throw from PASSED state on startKyc")
        void should_throw_when_callingStartKycFromPassed() {
            var passed = walkToTravelRulePackaging().completeTravelRule(travelRulePackage());

            assertThatThrownBy(passed::startKyc)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("should throw from FAILED state on passKyc")
        void should_throw_when_callingPassKycFromFailed() {
            var failed = createPendingCheck().startKyc().failKyc(failingKycResult());

            assertThatThrownBy(() -> failed.passKyc(passingKycResult()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("should throw from SANCTIONS_HIT state on sanctionsClear")
        void should_throw_when_callingMethodFromSanctionsHit() {
            var hit = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsHitDetected(hitSanctionsResult());

            assertThatThrownBy(() -> hit.sanctionsClear(clearSanctionsResult()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("should throw from MANUAL_REVIEW state on amlClear")
        void should_throw_when_callingMethodFromManualReview() {
            var review = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult())
                    .amlFlagged(flaggedAmlResult());

            assertThatThrownBy(() -> review.amlClear(clearAmlResult()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @ParameterizedTest(name = "should throw from terminal state {0} on failKyc")
        @EnumSource(value = ComplianceCheckStatus.class, names = {"PASSED", "FAILED", "MANUAL_REVIEW"})
        @DisplayName("should throw from any terminal state on failKyc")
        void should_throw_when_callingFailKycFromTerminalState(ComplianceCheckStatus terminalStatus) {
            var check = buildCheckInStatus(terminalStatus);

            assertThatThrownBy(() -> check.failKyc(failingKycResult()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @ParameterizedTest(name = "should throw from terminal state {0} on riskScored")
        @EnumSource(value = ComplianceCheckStatus.class, names = {"PASSED", "FAILED", "SANCTIONS_HIT", "MANUAL_REVIEW"})
        @DisplayName("should throw from any terminal state on riskScored")
        void should_throw_when_callingRiskScoredFromTerminalState(ComplianceCheckStatus terminalStatus) {
            var check = buildCheckInStatus(terminalStatus);

            assertThatThrownBy(() -> check.riskScored(lowRiskScore()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @ParameterizedTest(name = "should throw from terminal state {0} on completeTravelRule")
        @EnumSource(value = ComplianceCheckStatus.class, names = {"PASSED", "FAILED", "SANCTIONS_HIT", "MANUAL_REVIEW"})
        @DisplayName("should throw from any terminal state on completeTravelRule")
        void should_throw_when_callingCompleteTravelRuleFromTerminalState(ComplianceCheckStatus terminalStatus) {
            var check = buildCheckInStatus(terminalStatus);

            assertThatThrownBy(() -> check.completeTravelRule(travelRulePackage()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        private ComplianceCheck buildCheckInStatus(ComplianceCheckStatus status) {
            return ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .correlationId(UUID.randomUUID())
                    .senderId(SENDER_ID)
                    .recipientId(RECIPIENT_ID)
                    .sourceAmount(new BigDecimal("1000"))
                    .sourceCurrency("USD")
                    .targetCurrency("EUR")
                    .sourceCountry("US")
                    .targetCountry("DE")
                    .status(status)
                    .createdAt(Instant.now())
                    .build();
        }
    }

    // ── Invalid Transitions ──────────────────────────────────────────

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("should throw when calling sanctionsClear from PENDING")
        void should_throw_when_callingSanctionsClearFromPending() {
            var check = createPendingCheck();

            assertThatThrownBy(() -> check.sanctionsClear(clearSanctionsResult()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling amlClear from PENDING")
        void should_throw_when_callingAmlClearFromPending() {
            var check = createPendingCheck();

            assertThatThrownBy(() -> check.amlClear(clearAmlResult()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling riskScored from KYC_IN_PROGRESS")
        void should_throw_when_callingRiskScoredFromKycInProgress() {
            var check = createPendingCheck().startKyc();

            assertThatThrownBy(() -> check.riskScored(lowRiskScore()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling completeTravelRule from AML_SCREENING")
        void should_throw_when_callingCompleteTravelRuleFromAmlScreening() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult());

            assertThatThrownBy(() -> check.completeTravelRule(travelRulePackage()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling sanctionsHitDetected from PENDING")
        void should_throw_when_callingSanctionsHitFromPending() {
            var check = createPendingCheck();

            assertThatThrownBy(() -> check.sanctionsHitDetected(hitSanctionsResult()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling failTravelRule from RISK_SCORING")
        void should_throw_when_callingFailTravelRuleFromRiskScoring() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult())
                    .amlClear(clearAmlResult());

            assertThatThrownBy(() -> check.failTravelRule("fail"))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("should throw when calling startKyc from SANCTIONS_SCREENING")
        void should_throw_when_callingStartKycFromSanctionsScreening() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult());

            assertThatThrownBy(check::startKyc)
                    .isInstanceOf(StateMachineException.class);
        }
    }

    // ── isTerminal() Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("isTerminal()")
    class IsTerminal {

        @ParameterizedTest(name = "should return true for terminal state {0}")
        @EnumSource(value = ComplianceCheckStatus.class, names = {"PASSED", "FAILED", "SANCTIONS_HIT", "MANUAL_REVIEW"})
        @DisplayName("should return true for all terminal states")
        void should_returnTrue_when_statusIsTerminal(ComplianceCheckStatus terminalStatus) {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(terminalStatus)
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.isTerminal()).isTrue();
        }

        @ParameterizedTest(name = "should return false for non-terminal state {0}")
        @EnumSource(value = ComplianceCheckStatus.class, names = {"PENDING", "KYC_IN_PROGRESS", "SANCTIONS_SCREENING", "AML_SCREENING", "RISK_SCORING", "TRAVEL_RULE_PACKAGING"})
        @DisplayName("should return false for all non-terminal states")
        void should_returnFalse_when_statusIsNotTerminal(ComplianceCheckStatus nonTerminalStatus) {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(nonTerminalStatus)
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.isTerminal()).isFalse();
        }
    }

    // ── canApply() Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("canApply()")
    class CanApply {

        @Test
        @DisplayName("should return true for valid trigger from current state")
        void should_returnTrue_when_triggerIsValid() {
            var check = createPendingCheck();

            assertThat(check.canApply(START_KYC)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid trigger from current state")
        void should_returnFalse_when_triggerIsInvalid() {
            var check = createPendingCheck();

            assertThat(check.canApply(SANCTIONS_CLEAR)).isFalse();
            assertThat(check.canApply(AML_CLEAR)).isFalse();
            assertThat(check.canApply(RISK_SCORED)).isFalse();
            assertThat(check.canApply(TRAVEL_RULE_COMPLETE)).isFalse();
        }

        @Test
        @DisplayName("should return true for KYC_PASSED from KYC_IN_PROGRESS")
        void should_returnTrue_when_kycPassedFromKycInProgress() {
            var check = createPendingCheck().startKyc();

            assertThat(check.canApply(KYC_PASSED)).isTrue();
            assertThat(check.canApply(KYC_FAILED)).isTrue();
        }

        @Test
        @DisplayName("should return true for ESCALATE_MANUAL_REVIEW from SANCTIONS_HIT")
        void should_returnTrue_when_escalateFromSanctionsHit() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsHitDetected(hitSanctionsResult());

            assertThat(check.canApply(ESCALATE_MANUAL_REVIEW)).isTrue();
        }
    }

    // ── allSubChecksPassed() Tests ───────────────────────────────────

    @Nested
    @DisplayName("allSubChecksPassed()")
    class AllSubChecksPassed {

        @Test
        @DisplayName("should return true when all sub-checks are clear")
        void should_returnTrue_when_allSubChecksPassed() {
            var check = walkToTravelRulePackaging();

            assertThat(check.allSubChecksPassed()).isTrue();
        }

        @Test
        @DisplayName("should return false when kycResult is null")
        void should_returnFalse_when_kycResultIsNull() {
            var check = createPendingCheck();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when sender KYC not VERIFIED")
        void should_returnFalse_when_senderKycNotVerified() {
            var kycResult = KycResult.builder()
                    .senderStatus(KycStatus.REJECTED)
                    .recipientStatus(KycStatus.VERIFIED)
                    .senderKycTier(KycTier.KYC_TIER_1)
                    .build();

            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(SANCTIONS_SCREENING)
                    .kycResult(kycResult)
                    .sanctionsResult(clearSanctionsResult())
                    .amlResult(clearAmlResult())
                    .riskScore(lowRiskScore())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when sanctions result has a hit")
        void should_returnFalse_when_sanctionsHit() {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(SANCTIONS_HIT)
                    .kycResult(passingKycResult())
                    .sanctionsResult(hitSanctionsResult())
                    .amlResult(clearAmlResult())
                    .riskScore(lowRiskScore())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when AML is flagged")
        void should_returnFalse_when_amlFlagged() {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(MANUAL_REVIEW)
                    .kycResult(passingKycResult())
                    .sanctionsResult(clearSanctionsResult())
                    .amlResult(flaggedAmlResult())
                    .riskScore(lowRiskScore())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when riskScore is null")
        void should_returnFalse_when_riskScoreIsNull() {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(RISK_SCORING)
                    .kycResult(passingKycResult())
                    .sanctionsResult(clearSanctionsResult())
                    .amlResult(clearAmlResult())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when sanctionsResult is null")
        void should_returnFalse_when_sanctionsResultIsNull() {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(KYC_IN_PROGRESS)
                    .kycResult(passingKycResult())
                    .amlResult(clearAmlResult())
                    .riskScore(lowRiskScore())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }

        @Test
        @DisplayName("should return false when amlResult is null")
        void should_returnFalse_when_amlResultIsNull() {
            var check = ComplianceCheck.builder()
                    .checkId(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .status(SANCTIONS_SCREENING)
                    .kycResult(passingKycResult())
                    .sanctionsResult(clearSanctionsResult())
                    .riskScore(lowRiskScore())
                    .createdAt(Instant.now())
                    .build();

            assertThat(check.allSubChecksPassed()).isFalse();
        }
    }

    // ── Null Argument Guards ─────────────────────────────────────────

    @Nested
    @DisplayName("Null argument guards on transition methods")
    class NullArgumentGuards {

        @Test
        @DisplayName("should throw when passKyc receives null")
        void should_throw_when_passKycResultIsNull() {
            var check = createPendingCheck().startKyc();

            assertThatThrownBy(() -> check.passKyc(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("KYC result is required");
        }

        @Test
        @DisplayName("should throw when failKyc receives null")
        void should_throw_when_failKycResultIsNull() {
            var check = createPendingCheck().startKyc();

            assertThatThrownBy(() -> check.failKyc(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("KYC result is required");
        }

        @Test
        @DisplayName("should throw when sanctionsClear receives null")
        void should_throw_when_sanctionsClearResultIsNull() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult());

            assertThatThrownBy(() -> check.sanctionsClear(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sanctions result is required");
        }

        @Test
        @DisplayName("should throw when sanctionsHitDetected receives null")
        void should_throw_when_sanctionsHitDetectedResultIsNull() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult());

            assertThatThrownBy(() -> check.sanctionsHitDetected(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sanctions result is required");
        }

        @Test
        @DisplayName("should throw when amlClear receives null")
        void should_throw_when_amlClearResultIsNull() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult());

            assertThatThrownBy(() -> check.amlClear(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AML result is required");
        }

        @Test
        @DisplayName("should throw when amlFlagged receives null")
        void should_throw_when_amlFlaggedResultIsNull() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult());

            assertThatThrownBy(() -> check.amlFlagged(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AML result is required");
        }

        @Test
        @DisplayName("should throw when riskScored receives null")
        void should_throw_when_riskScoredIsNull() {
            var check = createPendingCheck()
                    .startKyc()
                    .passKyc(passingKycResult())
                    .sanctionsClear(clearSanctionsResult())
                    .amlClear(clearAmlResult());

            assertThatThrownBy(() -> check.riskScored(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Risk score is required");
        }
    }
}
