package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BeneficialOwner;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BusinessAddress;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import com.stablecoin.payments.merchant.onboarding.domain.statemachine.StateMachine;
import com.stablecoin.payments.merchant.onboarding.domain.statemachine.StateTransition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.ACTIVE;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.APPLIED;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.CLOSED;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.KYB_IN_PROGRESS;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.KYB_MANUAL_REVIEW;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.KYB_REJECTED;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.PENDING_APPROVAL;
import static com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus.SUSPENDED;

/**
 * Aggregate root for the Merchant bounded context.
 * All state changes go through domain methods that enforce the state machine.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Merchant {

    private static final StateMachine<MerchantStatus, MerchantTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    new StateTransition<>(APPLIED,            MerchantTrigger.START_KYB,          KYB_IN_PROGRESS),
                    new StateTransition<>(KYB_IN_PROGRESS,    MerchantTrigger.KYB_PASSED,         PENDING_APPROVAL),
                    new StateTransition<>(KYB_IN_PROGRESS,    MerchantTrigger.KYB_FLAGGED,        KYB_MANUAL_REVIEW),
                    new StateTransition<>(KYB_IN_PROGRESS,    MerchantTrigger.KYB_FAILED,         KYB_REJECTED),
                    new StateTransition<>(KYB_MANUAL_REVIEW,  MerchantTrigger.KYB_PASSED,         PENDING_APPROVAL),
                    new StateTransition<>(KYB_MANUAL_REVIEW,  MerchantTrigger.KYB_FAILED,         KYB_REJECTED),
                    new StateTransition<>(KYB_REJECTED,       MerchantTrigger.CLOSE,              CLOSED),
                    new StateTransition<>(PENDING_APPROVAL,   MerchantTrigger.APPROVE,            ACTIVE),
                    new StateTransition<>(ACTIVE,             MerchantTrigger.SUSPEND,            SUSPENDED),
                    new StateTransition<>(ACTIVE,             MerchantTrigger.CLOSE,              CLOSED),
                    new StateTransition<>(SUSPENDED,          MerchantTrigger.REACTIVATE,         ACTIVE),
                    new StateTransition<>(SUSPENDED,          MerchantTrigger.CLOSE,              CLOSED)
            ));

    private final UUID merchantId;
    private final String legalName;
    private final String tradingName;
    private final String registrationNumber;
    private final String registrationCountry;
    private final EntityType entityType;
    private final String websiteUrl;
    private final String primaryCurrency;
    private final String primaryContactEmail;
    private final String primaryContactName;
    private final BusinessAddress registeredAddress;
    private final List<BeneficialOwner> beneficialOwners;
    private final List<String> requestedCorridors;
    private final List<String> allowedScopes;
    private MerchantStatus status;
    private KybStatus kybStatus;
    private RiskTier riskTier;
    private RateLimitTier rateLimitTier;
    private UUID onboardedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant activatedAt;
    private Instant suspendedAt;
    private Instant closedAt;

    public static Merchant createNew(
            String legalName,
            String tradingName,
            String registrationNumber,
            String registrationCountry,
            EntityType entityType,
            String websiteUrl,
            String primaryCurrency,
            String primaryContactEmail,
            String primaryContactName,
            BusinessAddress registeredAddress,
            List<BeneficialOwner> beneficialOwners,
            List<String> requestedCorridors) {
        return Merchant.builder()
                .merchantId(UUID.randomUUID())
                .legalName(legalName)
                .tradingName(tradingName)
                .registrationNumber(registrationNumber)
                .registrationCountry(registrationCountry)
                .entityType(entityType)
                .websiteUrl(websiteUrl)
                .primaryCurrency(primaryCurrency)
                .primaryContactEmail(primaryContactEmail)
                .primaryContactName(primaryContactName)
                .registeredAddress(registeredAddress)
                .beneficialOwners(beneficialOwners != null ? new ArrayList<>(beneficialOwners) : new ArrayList<>())
                .requestedCorridors(requestedCorridors != null ? new ArrayList<>(requestedCorridors) : new ArrayList<>())
                .allowedScopes(new ArrayList<>())
                .status(APPLIED)
                .kybStatus(KybStatus.NOT_STARTED)
                .rateLimitTier(RateLimitTier.STARTER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public void startKyb() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.START_KYB);
        kybStatus = KybStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    public void kybPassed(RiskTier resolvedRiskTier) {
        status = STATE_MACHINE.transition(status, MerchantTrigger.KYB_PASSED);
        kybStatus = KybStatus.PASSED;
        riskTier = resolvedRiskTier;
        updatedAt = Instant.now();
    }

    public void kybFlaggedForManualReview() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.KYB_FLAGGED);
        kybStatus = KybStatus.MANUAL_REVIEW;
        updatedAt = Instant.now();
    }

    public void kybFailed() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.KYB_FAILED);
        kybStatus = KybStatus.FAILED;
        updatedAt = Instant.now();
    }

    public void activate(UUID approvedBy, List<String> scopes) {
        status = STATE_MACHINE.transition(status, MerchantTrigger.APPROVE);
        onboardedBy = approvedBy;
        allowedScopes.clear();
        allowedScopes.addAll(scopes);
        rateLimitTier = RateLimitTier.GROWTH;
        activatedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void suspend() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.SUSPEND);
        suspendedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void reactivate() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.REACTIVATE);
        suspendedAt = null;
        updatedAt = Instant.now();
    }

    public void close() {
        status = STATE_MACHINE.transition(status, MerchantTrigger.CLOSE);
        closedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void upgradeRateLimitTier(RateLimitTier newTier) {
        if (newTier.ordinal() <= rateLimitTier.ordinal()) {
            throw new IllegalArgumentException(
                    "Rate limit tier can only upgrade. current=%s requested=%s".formatted(rateLimitTier, newTier));
        }
        rateLimitTier = newTier;
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == ACTIVE;
    }
}
