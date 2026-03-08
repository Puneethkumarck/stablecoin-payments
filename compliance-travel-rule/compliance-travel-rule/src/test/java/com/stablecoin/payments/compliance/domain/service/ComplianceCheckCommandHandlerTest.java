package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.event.ComplianceCheckFailed;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckPassed;
import com.stablecoin.payments.compliance.domain.event.SanctionsHitEvent;
import com.stablecoin.payments.compliance.domain.exception.CheckNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.CustomerNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.DuplicatePaymentException;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheckStatus;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.OverallResult;
import com.stablecoin.payments.compliance.domain.model.RiskScoringWeights;
import com.stablecoin.payments.compliance.domain.port.AmlProvider;
import com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository;
import com.stablecoin.payments.compliance.domain.port.CustomerRiskProfileRepository;
import com.stablecoin.payments.compliance.domain.port.EventPublisher;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import com.stablecoin.payments.compliance.domain.port.TravelRuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycRejectedResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycTier1Result;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsClearResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsHitResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.anAmlClearResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.anAmlFlaggedResult;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ComplianceCheckCommandHandlerTest {

    @Mock private ComplianceCheckRepository checkRepository;
    @Mock private CustomerRiskProfileRepository profileRepository;
    @Mock private KycProvider kycProvider;
    @Mock private SanctionsProvider sanctionsProvider;
    @Mock private AmlProvider amlProvider;
    @Mock private TravelRuleProvider travelRuleProvider;
    @Mock private EventPublisher<Object> eventPublisher;

    private ComplianceCheckService complianceCheckService;
    private RiskScoringService riskScoringService;
    private ComplianceCheckCommandHandler handler;

    private static final Money ABOVE_THRESHOLD = new Money(new BigDecimal("1000.00"), "USD");
    private static final Money BELOW_THRESHOLD = new Money(new BigDecimal("500.00"), "USD");
    private static final Money HIGH_VALUE_AMOUNT = new Money(new BigDecimal("50000.00"), "USD");

    /**
     * Simple record used as comparison target for recursive comparison
     * when the domain object's builder is package-private.
     */
    private record ExpectedCheckState(
            UUID paymentId,
            ComplianceCheckStatus status,
            OverallResult overallResult,
            String errorMessage
    ) {}

    @BeforeEach
    void setUp() {
        complianceCheckService = new ComplianceCheckService();
        riskScoringService = new RiskScoringService(RiskScoringWeights.defaults());
        handler = new ComplianceCheckCommandHandler(
                checkRepository, profileRepository,
                kycProvider, sanctionsProvider, amlProvider, travelRuleProvider,
                complianceCheckService, riskScoringService, eventPublisher);
    }

    @Nested
    @DisplayName("Happy path -- full pipeline")
    class HappyPath {

        @Test
        @DisplayName("should complete full pipeline and return PASSED domain object")
        void shouldCompleteFullPipeline() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(senderId, recipientId)).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(senderId, recipientId))
                    .willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(senderId, recipientId)).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(senderId))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.PASSED, OverallResult.PASSED, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should save the completed check via repository")
        void shouldSaveCompletedCheck() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.PASSED, OverallResult.PASSED, null);

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish ComplianceCheckPassed event")
        void shouldPublishPassedEvent() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());

            var expected = new ComplianceCheckPassed(
                    null, paymentId, null, 0, null, null, null);

            assertThat(captor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "riskScore", "riskBand",
                            "travelRuleRef", "passedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should skip travel rule for below-threshold amounts")
        void shouldSkipTravelRuleBelowThreshold() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, BELOW_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.PASSED, OverallResult.PASSED, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
            then(travelRuleProvider).should(never()).transmit(any());
        }

        @Test
        @DisplayName("should invoke all providers in order for full pipeline")
        void shouldInvokeAllProvidersInOrder() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(senderId, recipientId)).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(senderId, recipientId))
                    .willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(senderId, recipientId)).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(senderId))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            then(kycProvider).should().verify(senderId, recipientId);
            then(sanctionsProvider).should().screen(senderId, recipientId);
            then(amlProvider).should().analyze(senderId, recipientId);
            then(travelRuleProvider).should().transmit(any());
            then(checkRepository).should().save(any(ComplianceCheck.class));
            then(eventPublisher).should().publish(any());
        }
    }

    @Nested
    @DisplayName("KYC failure")
    class KycFailure {

        @Test
        @DisplayName("should stop pipeline on KYC rejection and save FAILED check")
        void shouldStopOnKycRejection() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycRejectedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.FAILED, OverallResult.FAILED, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
            then(sanctionsProvider).should(never()).screen(any(), any());
            then(amlProvider).should(never()).analyze(any(), any());
        }

        @Test
        @DisplayName("should save check with FAILED status on KYC rejection")
        void shouldSaveFailedCheck() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycRejectedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.FAILED, OverallResult.FAILED,
                    "KYC verification failed");

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult", "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish ComplianceCheckFailed event on KYC rejection")
        void shouldPublishFailedEventOnKycRejection() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycRejectedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());

            var expected = new ComplianceCheckFailed(
                    null, paymentId, null, null, null, null);

            assertThat(captor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "reason", "errorCode", "failedAt")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Sanctions hit")
    class SanctionsHitPath {

        @Test
        @DisplayName("should stop pipeline on sanctions hit")
        void shouldStopOnSanctionsHit() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsHitResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.SANCTIONS_HIT,
                    OverallResult.SANCTIONS_HIT, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
            then(amlProvider).should(never()).analyze(any(), any());
        }

        @Test
        @DisplayName("should save check with SANCTIONS_HIT status")
        void shouldSaveSanctionsHitCheck() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsHitResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.SANCTIONS_HIT,
                    OverallResult.SANCTIONS_HIT,
                    "Sanctions screening hit detected");

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult", "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish both ComplianceCheckFailed and SanctionsHitEvent")
        void shouldPublishSanctionsHitEvents() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsHitResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should(times(2)).publish(captor.capture());
            var events = captor.getAllValues();

            var expectedFailed = new ComplianceCheckFailed(
                    null, paymentId, null, null, null, null);

            assertThat(events.get(0))
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "reason", "errorCode",
                            "failedAt")
                    .isEqualTo(expectedFailed);

            var expectedHit = new SanctionsHitEvent(
                    null, paymentId, null, "SENDER", "OFAC", null, null);

            assertThat(events.get(1))
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "hitDetails", "detectedAt")
                    .isEqualTo(expectedHit);
        }
    }

    @Nested
    @DisplayName("AML flagged")
    class AmlFlaggedPath {

        @Test
        @DisplayName("should stop pipeline on AML flag and route to MANUAL_REVIEW")
        void shouldStopOnAmlFlag() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlFlaggedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.MANUAL_REVIEW,
                    OverallResult.MANUAL_REVIEW, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
            then(travelRuleProvider).should(never()).transmit(any());
        }

        @Test
        @DisplayName("should save check with MANUAL_REVIEW status on AML flag")
        void shouldSaveManualReviewCheck() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlFlaggedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.MANUAL_REVIEW,
                    OverallResult.MANUAL_REVIEW,
                    "AML screening flagged — manual review required");

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult", "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish ComplianceCheckFailed event on AML flag")
        void shouldPublishFailedEventOnAmlFlag() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlFlaggedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());

            assertThat(captor.getValue()).isInstanceOf(ComplianceCheckFailed.class);

            var expected = new ComplianceCheckFailed(
                    null, paymentId, null, null, null, null);

            assertThat(captor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "reason", "errorCode",
                            "failedAt")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Risk scoring CRITICAL")
    class RiskCriticalPath {

        private ComplianceCheckCommandHandler criticalHandler;

        @BeforeEach
        void setUpCriticalHandler() {
            // Use corridor risk scores to push score above 75 (CRITICAL threshold)
            // KYC_TIER_1(20) + high_value(15) + cross_border(10) + corridor_US-DE(20) + new_customer(15) = 80 -> CRITICAL
            var weights = RiskScoringWeights.defaults().toBuilder()
                    .corridorRiskScores(Map.of("US-DE", 20))
                    .build();
            var criticalRiskService = new RiskScoringService(weights);
            criticalHandler = new ComplianceCheckCommandHandler(
                    checkRepository, profileRepository,
                    kycProvider, sanctionsProvider, amlProvider, travelRuleProvider,
                    complianceCheckService, criticalRiskService, eventPublisher);
        }

        @Test
        @DisplayName("should route to MANUAL_REVIEW on CRITICAL risk score")
        void shouldRouteToManualReviewOnCriticalRisk() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycTier1Result(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.empty());
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = criticalHandler.initiateCheck(
                    paymentId, senderId, recipientId, HIGH_VALUE_AMOUNT, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.MANUAL_REVIEW,
                    OverallResult.MANUAL_REVIEW, null);

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult")
                    .isEqualTo(expected);
            then(travelRuleProvider).should(never()).transmit(any());
        }

        @Test
        @DisplayName("should save check with MANUAL_REVIEW status on CRITICAL risk")
        void shouldSaveManualReviewCheckOnCriticalRisk() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycTier1Result(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.empty());
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            criticalHandler.initiateCheck(
                    paymentId, senderId, recipientId, HIGH_VALUE_AMOUNT, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.MANUAL_REVIEW,
                    OverallResult.MANUAL_REVIEW,
                    "Critical risk score — manual review required");

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult", "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish ComplianceCheckFailed event on CRITICAL risk")
        void shouldPublishFailedEventOnCriticalRisk() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycTier1Result(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.empty());
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            criticalHandler.initiateCheck(
                    paymentId, senderId, recipientId, HIGH_VALUE_AMOUNT, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());

            assertThat(captor.getValue()).isInstanceOf(ComplianceCheckFailed.class);

            var expected = new ComplianceCheckFailed(
                    null, paymentId, null, null, null, null);

            assertThat(captor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("checkId", "correlationId", "reason", "errorCode",
                            "failedAt")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Travel rule failure")
    class TravelRuleFailure {

        @Test
        @DisplayName("should handle travel rule transmission failure gracefully")
        void shouldHandleTravelRuleFailure() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any()))
                    .willThrow(new RuntimeException("Network error"));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var result = handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.FAILED, OverallResult.FAILED,
                    "Travel rule transmission failed: Network error");

            assertThat(result)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult",
                            "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should save FAILED check on travel rule transmission error")
        void shouldSaveFailedCheckOnTravelRuleError() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any()))
                    .willThrow(new RuntimeException("Connection refused"));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(ComplianceCheck.class);
            then(checkRepository).should().save(captor.capture());

            var saved = captor.getValue();
            var expected = new ExpectedCheckState(
                    paymentId, ComplianceCheckStatus.FAILED, OverallResult.FAILED,
                    "Travel rule transmission failed: Connection refused");

            assertThat(saved)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("paymentId", "status", "overallResult", "errorMessage")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should publish ComplianceCheckFailed event on travel rule failure")
        void shouldPublishFailedEventOnTravelRuleFailure() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            given(checkRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any()))
                    .willThrow(new RuntimeException("Timeout"));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());

            assertThat(captor.getValue()).isInstanceOf(ComplianceCheckFailed.class);
        }
    }

    @Nested
    @DisplayName("Duplicate payment")
    class DuplicatePayment {

        @Test
        @DisplayName("should throw DuplicatePaymentException when check already exists")
        void shouldThrowOnDuplicate() {
            var paymentId = UUID.randomUUID();
            var senderId = UUID.randomUUID();
            var recipientId = UUID.randomUUID();
            var existingCheck = ComplianceCheck.initiate(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR");
            given(checkRepository.findByPaymentId(paymentId))
                    .willReturn(Optional.of(existingCheck));

            assertThatThrownBy(() -> handler.initiateCheck(
                    paymentId, senderId, recipientId, ABOVE_THRESHOLD, "US", "DE", "EUR"))
                    .isInstanceOf(DuplicatePaymentException.class)
                    .hasMessageContaining(paymentId.toString());

            then(kycProvider).should(never()).verify(any(), any());
            then(checkRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get check")
    class GetCheck {

        @Test
        @DisplayName("should return domain check for existing check")
        void shouldReturnCheck() {
            var checkId = UUID.randomUUID();
            var check = ComplianceCheck.initiate(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    ABOVE_THRESHOLD, "US", "DE", "EUR");
            given(checkRepository.findById(checkId)).willReturn(Optional.of(check));

            var result = handler.getCheck(checkId);

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(check);
        }

        @Test
        @DisplayName("should throw CheckNotFoundException for missing check")
        void shouldThrowOnMissingCheck() {
            var checkId = UUID.randomUUID();
            given(checkRepository.findById(checkId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getCheck(checkId))
                    .isInstanceOf(CheckNotFoundException.class)
                    .hasMessageContaining(checkId.toString());
        }
    }

    @Nested
    @DisplayName("Get customer risk profile")
    class GetCustomerProfile {

        @Test
        @DisplayName("should return customer risk profile domain object")
        void shouldReturnProfile() {
            var customerId = UUID.randomUUID();
            var profile = aRiskProfile().toBuilder().customerId(customerId).build();
            given(profileRepository.findByCustomerId(customerId))
                    .willReturn(Optional.of(profile));

            var result = handler.getCustomerRiskProfile(customerId);

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(profile);
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException for missing profile")
        void shouldThrowOnMissingProfile() {
            var customerId = UUID.randomUUID();
            given(profileRepository.findByCustomerId(customerId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getCustomerRiskProfile(customerId))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining(customerId.toString());
        }
    }
}
