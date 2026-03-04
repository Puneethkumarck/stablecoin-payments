package com.stablecoin.payments.merchant.onboarding.domain.merchant;

import com.stablecoin.payments.merchant.onboarding.domain.EventPublisher;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantAlreadyExistsException;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantNotFoundException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.command.ApplyMerchantCommand;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.BusinessAddress;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.DocumentUploadResult;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatusResult;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantActivatedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantAppliedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantClosedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantCorridorApprovedEvent;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.events.MerchantSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantCommandHandler {

    private final MerchantRepository merchantRepository;
    private final KybProvider kybProvider;
    private final EventPublisher<Object> eventPublisher;
    private final MerchantActivationPolicy activationPolicy;
    private final CorridorEntitlementService corridorEntitlementService;
    private final DocumentStore documentStore;
    private final ApprovedCorridorRepository approvedCorridorRepository;

    @Transactional
    public Merchant apply(ApplyMerchantCommand command) {
        if (merchantRepository.existsByRegistrationNumberAndCountry(
                command.registrationNumber(), command.registrationCountry())) {
            throw MerchantAlreadyExistsException.withRegistration(
                    command.registrationNumber(), command.registrationCountry());
        }

        var merchant = Merchant.createNew(
                command.legalName(),
                command.tradingName(),
                command.registrationNumber(),
                command.registrationCountry(),
                command.entityType(),
                command.websiteUrl(),
                command.primaryCurrency(),
                command.registeredAddress(),
                command.beneficialOwners(),
                command.requestedCorridors()
        );

        var saved = merchantRepository.save(merchant);
        log.info("Merchant applied merchantId={} legalName={}", saved.getMerchantId(), saved.getLegalName());

        eventPublisher.publish(MerchantAppliedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantAppliedEvent.EVENT_TYPE)
                .merchantId(saved.getMerchantId())
                .correlationId(correlationId())
                .legalName(saved.getLegalName())
                .registrationCountry(saved.getRegistrationCountry())
                .entityType(saved.getEntityType().name())
                .appliedAt(saved.getCreatedAt())
                .build());

        return saved;
    }

    @Transactional
    public void startKyb(UUID merchantId) {
        var merchant = findOrThrow(merchantId);
        merchant.startKyb();

        var kyb = kybProvider.submit(
                merchant.getMerchantId(),
                merchant.getLegalName(),
                merchant.getRegistrationNumber(),
                merchant.getRegistrationCountry());

        merchantRepository.save(merchant);
        log.info("KYB started merchantId={} kybId={} providerRef={}", merchantId, kyb.kybId(), kyb.providerRef());
    }

    @Transactional(readOnly = true)
    public KybStatusResult getKybStatus(UUID merchantId) {
        var merchant = findOrThrow(merchantId);
        var kybResult = kybProvider.getResult("merchant-" + merchantId);
        if (kybResult.isPresent()) {
            var kyb = kybResult.get();
            return new KybStatusResult(
                    kyb.kybId(), kyb.status().name(), kyb.provider(),
                    kyb.providerRef(), kyb.initiatedAt(), kyb.completedAt(),
                    kyb.riskSignals());
        }
        return new KybStatusResult(
                null, merchant.getKybStatus().name(), null,
                null, null, null, null);
    }

    @Transactional
    public Merchant activate(UUID merchantId, UUID approvedBy, List<String> scopes) {
        var merchant = findOrThrow(merchantId);
        activationPolicy.validate(merchant);
        merchant.activate(approvedBy, scopes);
        var saved = merchantRepository.save(merchant);
        log.info("Merchant activated merchantId={}", merchantId);

        eventPublisher.publish(MerchantActivatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantActivatedEvent.EVENT_TYPE)
                .merchantId(saved.getMerchantId())
                .correlationId(correlationId())
                .legalName(saved.getLegalName())
                .riskTier(saved.getRiskTier() != null ? saved.getRiskTier().name() : null)
                .rateLimitTier(saved.getRateLimitTier().name())
                .allowedScopes(saved.getAllowedScopes())
                .primaryCurrency(saved.getPrimaryCurrency())
                .activatedAt(saved.getActivatedAt())
                .build());

        return saved;
    }

    @Transactional
    public void suspend(UUID merchantId, String reason, UUID suspendedBy) {
        var merchant = findOrThrow(merchantId);
        merchant.suspend();
        merchantRepository.save(merchant);
        log.info("Merchant suspended merchantId={} reason={}", merchantId, reason);

        eventPublisher.publish(MerchantSuspendedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantSuspendedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .correlationId(correlationId())
                .reason(reason)
                .suspendedBy(suspendedBy)
                .suspendedAt(merchant.getSuspendedAt())
                .build());
    }

    @Transactional
    public void reactivate(UUID merchantId) {
        var merchant = findOrThrow(merchantId);
        merchant.reactivate();
        merchantRepository.save(merchant);
        log.info("Merchant reactivated merchantId={}", merchantId);
    }

    @Transactional
    public void close(UUID merchantId, String reason, UUID closedBy) {
        var merchant = findOrThrow(merchantId);
        merchant.close();
        merchantRepository.save(merchant);
        log.info("Merchant closed merchantId={} reason={}", merchantId, reason);

        eventPublisher.publish(MerchantClosedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantClosedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .correlationId(correlationId())
                .reason(reason)
                .closedBy(closedBy)
                .closedAt(merchant.getClosedAt())
                .build());
    }

    @Transactional
    public Merchant updateMerchant(UUID merchantId, String tradingName, String websiteUrl, BusinessAddress registeredAddress) {
        var merchant = findOrThrow(merchantId);
        var builder = merchant.toBuilder();
        if (tradingName != null) {
            builder.tradingName(tradingName);
        }
        if (websiteUrl != null) {
            builder.websiteUrl(websiteUrl);
        }
        if (registeredAddress != null) {
            builder.registeredAddress(registeredAddress);
        }
        var updated = builder.updatedAt(Instant.now()).build();
        var saved = merchantRepository.save(updated);
        log.info("Merchant updated merchantId={}", merchantId);
        return saved;
    }

    @Transactional
    public DocumentUploadResult uploadDocument(UUID merchantId, String documentType, String fileName) {
        findOrThrow(merchantId);
        var uploadUrl = documentStore.generateUploadUrl(merchantId, documentType, fileName);
        log.info("Document upload URL generated merchantId={} documentType={}", merchantId, documentType);
        return new DocumentUploadResult(uploadUrl, Instant.now().plusSeconds(3600));
    }

    @Transactional
    public Merchant updateRateLimitTier(UUID merchantId, String newTier) {
        var merchant = findOrThrow(merchantId);
        var tier = RateLimitTier.valueOf(newTier);
        merchant.upgradeRateLimitTier(tier);
        var saved = merchantRepository.save(merchant);
        log.info("Rate limit tier updated merchantId={} newTier={}", merchantId, tier);
        return saved;
    }

    @Transactional
    public ApprovedCorridor approveCorridor(UUID merchantId, String sourceCountry, String targetCountry,
                                            List<String> currencies, BigDecimal maxAmountUsd,
                                            Instant expiresAt, UUID approvedBy) {
        var merchant = findOrThrow(merchantId);
        corridorEntitlementService.validate(merchant, sourceCountry, targetCountry);

        var corridor = ApprovedCorridor.builder()
                .corridorId(UUID.randomUUID())
                .merchantId(merchantId)
                .sourceCountry(sourceCountry)
                .targetCountry(targetCountry)
                .currencies(currencies)
                .maxAmountUsd(maxAmountUsd)
                .approvedBy(approvedBy)
                .approvedAt(Instant.now())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

        var saved = approvedCorridorRepository.save(corridor);
        log.info("Corridor approved merchantId={} corridorId={}", merchantId, saved.corridorId());

        eventPublisher.publish(MerchantCorridorApprovedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantCorridorApprovedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .correlationId(correlationId())
                .corridorId(saved.corridorId())
                .sourceCountry(corridor.sourceCountry())
                .targetCountry(corridor.targetCountry())
                .maxAmountUsd(corridor.maxAmountUsd().toPlainString())
                .approvedAt(corridor.approvedAt())
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Merchant findById(UUID merchantId) {
        return findOrThrow(merchantId);
    }

    private Merchant findOrThrow(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> MerchantNotFoundException.withId(merchantId));
    }

    private String correlationId() {
        var id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
