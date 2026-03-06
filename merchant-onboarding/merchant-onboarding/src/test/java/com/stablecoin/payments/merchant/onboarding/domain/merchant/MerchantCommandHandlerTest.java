package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.EventPublisher;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantNotFoundException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.command.ApplyMerchantCommand;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantActivatedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantAppliedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantClosedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantCorridorApprovedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantSuspendedEvent;
import com.stablecoin.payments.merchant.onboarding.fixtures.MerchantFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.merchant.onboarding.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.merchant.onboarding.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantCommandHandler")
class MerchantCommandHandlerTest {

    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private KybProvider kybProvider;
    @Mock
    private EventPublisher<Object> eventPublisher;
    @Mock
    private MerchantActivationPolicy activationPolicy;
    @Mock
    private CorridorEntitlementService corridorEntitlementService;
    @Mock
    private DocumentStore documentStore;
    @Mock
    private ApprovedCorridorRepository approvedCorridorRepository;
    @Mock
    private OnboardingWorkflowPort onboardingWorkflowPort;

    @InjectMocks
    private MerchantCommandHandler handler;

    @Test
    @DisplayName("should apply merchant and publish event")
    void shouldApplyMerchant() {
        // given
        var command = new ApplyMerchantCommand(
                "Acme Ltd", "Acme", "REG-123", "GB", EntityType.PRIVATE_LIMITED,
                "https://acme.com", "USD", "admin@acme.com", "Admin User",
                null, null, List.of("GB->US"));
        given(merchantRepository.existsByRegistrationNumberAndCountry("REG-123", "GB"))
                .willReturn(false);
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = Merchant.createNew(
                "Acme Ltd", "Acme", "REG-123", "GB", EntityType.PRIVATE_LIMITED,
                "https://acme.com", "USD", "admin@acme.com", "Admin User",
                null, null, List.of("GB->US"));

        var expectedEvent = MerchantAppliedEvent.builder()
                .eventType(MerchantAppliedEvent.EVENT_TYPE)
                .legalName("Acme Ltd")
                .registrationCountry("GB")
                .entityType(EntityType.PRIVATE_LIMITED.name())
                .build();

        // when
        handler.apply(command);

        // then
        then(merchantRepository).should().save(
                eqIgnoring(expectedMerchant, "merchantId"));
        then(eventPublisher).should().publish(
                eqIgnoring(expectedEvent, "eventId", "correlationId", "merchantId"));
    }

    @Test
    @DisplayName("should start KYB verification via onboarding workflow")
    void shouldStartKyb() {
        // given
        var merchant = MerchantFixtures.appliedMerchant();
        var merchantId = merchant.getMerchantId();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = merchant.toBuilder().build();
        expectedMerchant.startKyb();

        // when
        handler.startKyb(merchantId);

        // then
        then(merchantRepository).should().save(eqIgnoringTimestamps(expectedMerchant));
        then(onboardingWorkflowPort).should().startOnboarding(merchantId);
    }

    @Test
    @DisplayName("should activate merchant with policy validation")
    void shouldActivateMerchant() {
        // given
        var merchant = MerchantFixtures.pendingApprovalMerchant();
        var merchantId = merchant.getMerchantId();
        var approver = MerchantFixtures.anApprover();
        var scopes = List.of("payments:read", "payments:write");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = merchant.toBuilder().build();
        expectedMerchant.activate(approver, scopes);

        var expectedEvent = MerchantActivatedEvent.builder()
                .eventType(MerchantActivatedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .legalName(merchant.getLegalName())
                .companyName(merchant.getLegalName())
                .primaryContactEmail(merchant.getPrimaryContactEmail())
                .primaryContactName(merchant.getPrimaryContactName())
                .country(merchant.getRegistrationCountry())
                .scopes(scopes)
                .riskTier(merchant.getRiskTier() != null ? merchant.getRiskTier().name() : null)
                .rateLimitTier(RateLimitTier.GROWTH.name())
                .allowedScopes(scopes)
                .primaryCurrency(merchant.getPrimaryCurrency())
                .build();

        // when
        handler.activate(merchantId, approver, scopes);

        // then
        then(activationPolicy).should().validate(merchant);
        then(merchantRepository).should().save(eqIgnoringTimestamps(expectedMerchant));
        then(eventPublisher).should().publish(
                eqIgnoring(expectedEvent, "eventId", "correlationId"));
    }

    @Test
    @DisplayName("should reject activation when policy fails")
    void shouldRejectActivationWhenPolicyFails() {
        // given
        var merchant = MerchantFixtures.appliedMerchant();
        var merchantId = merchant.getMerchantId();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        willThrow(new IllegalStateException("KYB not passed"))
                .given(activationPolicy).validate(any());

        // when / then
        assertThatThrownBy(() -> handler.activate(merchantId, MerchantFixtures.anApprover(), List.of("payments:read")))
                .isInstanceOf(IllegalStateException.class);
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("should suspend merchant and publish event")
    void shouldSuspendMerchant() {
        // given
        var merchant = MerchantFixtures.activeMerchant();
        var merchantId = merchant.getMerchantId();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = merchant.toBuilder().build();
        expectedMerchant.suspend();

        var expectedEvent = MerchantSuspendedEvent.builder()
                .eventType(MerchantSuspendedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .reason("compliance review")
                .build();

        // when
        handler.suspend(merchantId, "compliance review", null);

        // then
        then(merchantRepository).should().save(eqIgnoringTimestamps(expectedMerchant));
        then(eventPublisher).should().publish(
                eqIgnoring(expectedEvent, "eventId", "correlationId"));
    }

    @Test
    @DisplayName("should reactivate merchant")
    void shouldReactivateMerchant() {
        // given
        var merchant = MerchantFixtures.suspendedMerchant();
        var merchantId = merchant.getMerchantId();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = merchant.toBuilder().build();
        expectedMerchant.reactivate();

        // when
        handler.reactivate(merchantId);

        // then
        then(merchantRepository).should().save(eqIgnoringTimestamps(expectedMerchant));
    }

    @Test
    @DisplayName("should close merchant and publish event")
    void shouldCloseMerchant() {
        // given
        var merchant = MerchantFixtures.activeMerchant();
        var merchantId = merchant.getMerchantId();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.save(any(Merchant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedMerchant = merchant.toBuilder().build();
        expectedMerchant.close();

        var expectedEvent = MerchantClosedEvent.builder()
                .eventType(MerchantClosedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .reason("business closure")
                .build();

        // when
        handler.close(merchantId, "business closure", null);

        // then
        then(merchantRepository).should().save(eqIgnoringTimestamps(expectedMerchant));
        then(eventPublisher).should().publish(
                eqIgnoring(expectedEvent, "eventId", "correlationId"));
    }

    @Test
    @DisplayName("should approve corridor with entitlement validation")
    void shouldApproveCorridor() {
        // given
        var merchant = MerchantFixtures.activeMerchant();
        var merchantId = merchant.getMerchantId();
        var approvedBy = MerchantFixtures.anApprover();
        var expiresAt = Instant.now().plusSeconds(86400);
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(approvedCorridorRepository.save(any(ApprovedCorridor.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var expectedCorridor = ApprovedCorridor.builder()
                .merchantId(merchantId)
                .sourceCountry("GB")
                .targetCountry("US")
                .currencies(List.of("GBP", "USD"))
                .maxAmountUsd(new BigDecimal("100000"))
                .approvedBy(approvedBy)
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

        var expectedEvent = MerchantCorridorApprovedEvent.builder()
                .eventType(MerchantCorridorApprovedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .sourceCountry("GB")
                .targetCountry("US")
                .maxAmountUsd("100000")
                .build();

        // when
        handler.approveCorridor(merchantId, "GB", "US",
                List.of("GBP", "USD"), new BigDecimal("100000"), expiresAt, approvedBy);

        // then
        then(corridorEntitlementService).should().validate(merchant, "GB", "US");
        then(approvedCorridorRepository).should().save(
                eqIgnoring(expectedCorridor, "corridorId"));
        then(eventPublisher).should().publish(
                eqIgnoring(expectedEvent, "eventId", "correlationId", "corridorId"));
    }

    @Test
    @DisplayName("should throw when merchant not found")
    void shouldThrowWhenMerchantNotFound() {
        // given
        var merchantId = UUID.randomUUID();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> handler.findById(merchantId))
                .isInstanceOf(MerchantNotFoundException.class);
    }
}
