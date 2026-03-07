package com.stablecoin.payments.compliance.application.service;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.application.mapper.ComplianceCheckResponseMapper;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckFailed;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckPassed;
import com.stablecoin.payments.compliance.domain.event.SanctionsHitEvent;
import com.stablecoin.payments.compliance.domain.exception.CheckNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.CustomerNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.DuplicatePaymentException;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.RiskScoringContext;
import com.stablecoin.payments.compliance.domain.model.RiskScoringWeights;
import com.stablecoin.payments.compliance.domain.port.AmlProvider;
import com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository;
import com.stablecoin.payments.compliance.domain.port.CustomerRiskProfileRepository;
import com.stablecoin.payments.compliance.domain.port.EventPublisher;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import com.stablecoin.payments.compliance.domain.port.TravelRuleProvider;
import com.stablecoin.payments.compliance.domain.service.ComplianceCheckService;
import com.stablecoin.payments.compliance.domain.service.RiskScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycRejectedResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.anAmlClearResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.anAmlFlaggedResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsClearResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsHitResult;
import static com.stablecoin.payments.compliance.fixtures.CustomerRiskProfileFixtures.aRiskProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ComplianceCheckApplicationServiceTest {

    @Mock private ComplianceCheckRepository checkRepository;
    @Mock private CustomerRiskProfileRepository profileRepository;
    @Mock private KycProvider kycProvider;
    @Mock private SanctionsProvider sanctionsProvider;
    @Mock private AmlProvider amlProvider;
    @Mock private TravelRuleProvider travelRuleProvider;
    @Mock private EventPublisher<Object> eventPublisher;

    private ComplianceCheckService complianceCheckService;
    private RiskScoringService riskScoringService;
    private ComplianceCheckResponseMapper responseMapper;
    private ComplianceCheckApplicationService service;

    @BeforeEach
    void setUp() {
        complianceCheckService = new ComplianceCheckService();
        riskScoringService = new RiskScoringService(RiskScoringWeights.defaults());
        responseMapper = new ComplianceCheckResponseMapper();
        service = new ComplianceCheckApplicationService(
                checkRepository, profileRepository,
                kycProvider, sanctionsProvider, amlProvider, travelRuleProvider,
                complianceCheckService, riskScoringService,
                eventPublisher, responseMapper);
    }

    private static InitiateComplianceCheckRequest aValidRequest() {
        return new InitiateComplianceCheckRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD", "US", "DE", "EUR");
    }

    private static InitiateComplianceCheckRequest aBelowThresholdRequest() {
        return new InitiateComplianceCheckRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("500.00"), "USD", "US", "DE", "EUR");
    }

    @Nested
    @DisplayName("Happy path — full pipeline")
    class HappyPath {

        @Test
        @DisplayName("should complete full pipeline and return PASSED")
        void shouldCompleteFullPipeline() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(request.senderId(), request.recipientId()))
                    .willReturn(aKycResult(null));
            given(sanctionsProvider.screen(request.senderId(), request.recipientId()))
                    .willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(request.senderId(), request.recipientId()))
                    .willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(request.senderId()))
                    .willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("PASSED");
            assertThat(response.overallResult()).isEqualTo("PASSED");
            assertThat(response.paymentId()).isEqualTo(request.paymentId());
            assertThat(response.riskScore()).isNotNull();
            assertThat(response.kycResult()).isNotNull();
            assertThat(response.sanctionsResult()).isNotNull();
            assertThat(response.travelRule()).isNotNull();
            assertThat(response.travelRule().transmissionStatus()).isEqualTo("TRANSMITTED");
        }

        @Test
        @DisplayName("should publish ComplianceCheckPassed event")
        void shouldPublishPassedEvent() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willReturn("tr-ref-123");
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            service.initiateCheck(request);

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ComplianceCheckPassed.class);
            var event = (ComplianceCheckPassed) captor.getValue();
            assertThat(event.paymentId()).isEqualTo(request.paymentId());
        }

        @Test
        @DisplayName("should skip travel rule for below-threshold amounts")
        void shouldSkipTravelRuleBelowThreshold() {
            var request = aBelowThresholdRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.of(aRiskProfile()));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("PASSED");
            assertThat(response.travelRule()).isNull();
            then(travelRuleProvider).should(never()).transmit(any());
        }
    }

    @Nested
    @DisplayName("KYC failure")
    class KycFailure {

        @Test
        @DisplayName("should stop pipeline on KYC rejection")
        void shouldStopOnKycRejection() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycRejectedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("FAILED");
            assertThat(response.overallResult()).isEqualTo("FAILED");
            then(sanctionsProvider).should(never()).screen(any(), any());
            then(amlProvider).should(never()).analyze(any(), any());
        }

        @Test
        @DisplayName("should publish ComplianceCheckFailed event on KYC rejection")
        void shouldPublishFailedEventOnKycRejection() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycRejectedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            service.initiateCheck(request);

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should().publish(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ComplianceCheckFailed.class);
        }
    }

    @Nested
    @DisplayName("Sanctions hit")
    class SanctionsHitPath {

        @Test
        @DisplayName("should stop pipeline on sanctions hit")
        void shouldStopOnSanctionsHit() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsHitResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("SANCTIONS_HIT");
            assertThat(response.overallResult()).isEqualTo("SANCTIONS_HIT");
            then(amlProvider).should(never()).analyze(any(), any());
        }

        @Test
        @DisplayName("should publish both ComplianceCheckFailed and SanctionsHitEvent")
        void shouldPublishSanctionsHitEvents() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsHitResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            service.initiateCheck(request);

            var captor = ArgumentCaptor.forClass(Object.class);
            then(eventPublisher).should(times(2)).publish(captor.capture());
            var events = captor.getAllValues();
            assertThat(events.get(0)).isInstanceOf(ComplianceCheckFailed.class);
            assertThat(events.get(1)).isInstanceOf(SanctionsHitEvent.class);
            var hitEvent = (SanctionsHitEvent) events.get(1);
            assertThat(hitEvent.hitParty()).isEqualTo("SENDER");
        }
    }

    @Nested
    @DisplayName("AML flagged")
    class AmlFlaggedPath {

        @Test
        @DisplayName("should stop pipeline on AML flag and route to MANUAL_REVIEW")
        void shouldStopOnAmlFlag() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlFlaggedResult(null));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("MANUAL_REVIEW");
            assertThat(response.overallResult()).isEqualTo("MANUAL_REVIEW");
            then(travelRuleProvider).should(never()).transmit(any());
        }
    }

    @Nested
    @DisplayName("Travel rule failure")
    class TravelRuleFailure {

        @Test
        @DisplayName("should handle travel rule transmission failure gracefully")
        void shouldHandleTravelRuleFailure() {
            var request = aValidRequest();
            given(checkRepository.findByPaymentId(request.paymentId())).willReturn(Optional.empty());
            given(kycProvider.verify(any(), any())).willReturn(aKycResult(null));
            given(sanctionsProvider.screen(any(), any())).willReturn(aSanctionsClearResult(null));
            given(amlProvider.analyze(any(), any())).willReturn(anAmlClearResult(null));
            given(profileRepository.findByCustomerId(any())).willReturn(Optional.of(aRiskProfile()));
            given(travelRuleProvider.transmit(any())).willThrow(new RuntimeException("Network error"));
            given(checkRepository.save(any(ComplianceCheck.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            var response = service.initiateCheck(request);

            assertThat(response.status()).isEqualTo("FAILED");
            assertThat(response.overallResult()).isEqualTo("FAILED");
            assertThat(response.errorMessage()).contains("Travel rule transmission failed");
        }
    }

    @Nested
    @DisplayName("Duplicate payment")
    class DuplicatePayment {

        @Test
        @DisplayName("should throw DuplicatePaymentException when check already exists")
        void shouldThrowOnDuplicate() {
            var request = aValidRequest();
            var existingCheck = ComplianceCheck.initiate(
                    request.paymentId(), request.senderId(), request.recipientId(),
                    new com.stablecoin.payments.compliance.domain.model.Money(request.amount(), request.currency()),
                    request.sourceCountry(), request.targetCountry(), request.targetCurrency());
            given(checkRepository.findByPaymentId(request.paymentId()))
                    .willReturn(Optional.of(existingCheck));

            assertThatThrownBy(() -> service.initiateCheck(request))
                    .isInstanceOf(DuplicatePaymentException.class)
                    .hasMessageContaining(request.paymentId().toString());

            then(kycProvider).should(never()).verify(any(), any());
        }
    }

    @Nested
    @DisplayName("Get check")
    class GetCheck {

        @Test
        @DisplayName("should return check response for existing check")
        void shouldReturnCheckResponse() {
            var checkId = UUID.randomUUID();
            var check = ComplianceCheck.initiate(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new com.stablecoin.payments.compliance.domain.model.Money(new BigDecimal("1000.00"), "USD"),
                    "US", "DE", "EUR");
            given(checkRepository.findById(checkId)).willReturn(Optional.of(check));

            var response = service.getCheck(checkId);

            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.checkId()).isEqualTo(check.checkId());
        }

        @Test
        @DisplayName("should throw CheckNotFoundException for missing check")
        void shouldThrowOnMissingCheck() {
            var checkId = UUID.randomUUID();
            given(checkRepository.findById(checkId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCheck(checkId))
                    .isInstanceOf(CheckNotFoundException.class)
                    .hasMessageContaining(checkId.toString());
        }
    }

    @Nested
    @DisplayName("Get customer risk profile")
    class GetCustomerProfile {

        @Test
        @DisplayName("should return customer risk profile response")
        void shouldReturnProfileResponse() {
            var customerId = UUID.randomUUID();
            var profile = aRiskProfile().toBuilder().customerId(customerId).build();
            given(profileRepository.findByCustomerId(customerId)).willReturn(Optional.of(profile));

            var response = service.getCustomerRiskProfile(customerId);

            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.kycTier()).isEqualTo("KYC_TIER_2");
            assertThat(response.riskBand()).isEqualTo("LOW");
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException for missing profile")
        void shouldThrowOnMissingProfile() {
            var customerId = UUID.randomUUID();
            given(profileRepository.findByCustomerId(customerId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCustomerRiskProfile(customerId))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining(customerId.toString());
        }
    }
}
