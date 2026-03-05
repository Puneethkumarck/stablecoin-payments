package com.stablecoin.payments.merchant.onboarding.application.service;

import com.stablecoin.payments.merchant.onboarding.api.request.ActivateMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.ApproveCorridorRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.CloseMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.DocumentUploadRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.MerchantApplicationRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.SuspendMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.UpdateMerchantRequest;
import com.stablecoin.payments.merchant.onboarding.api.request.UpdateRateLimitTierRequest;
import com.stablecoin.payments.merchant.onboarding.api.response.CorridorResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.DocumentUploadResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.KybStatusResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantApplicationResponse;
import com.stablecoin.payments.merchant.onboarding.api.response.MerchantResponse;
import com.stablecoin.payments.merchant.onboarding.application.controller.MerchantRequestResponseMapper;
import com.stablecoin.payments.merchant.onboarding.domain.EventPublisher;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantAlreadyExistsException;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantNotFoundException;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.ApprovedCorridorRepository;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.CorridorEntitlementService;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.DocumentStore;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.Merchant;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantActivationPolicy;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.MerchantRepository;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
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

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApplicationService {

    private final MerchantRepository merchantRepository;
    private final KybProvider kybProvider;
    private final EventPublisher<Object> eventPublisher;
    private final MerchantRequestResponseMapper responseMapper;
    private final MerchantActivationPolicy activationPolicy;
    private final CorridorEntitlementService corridorEntitlementService;
    private final DocumentStore documentStore;
    private final ApprovedCorridorRepository approvedCorridorRepository;

    @Transactional
    public MerchantApplicationResponse apply(MerchantApplicationRequest request) {
        if (merchantRepository.existsByRegistrationNumberAndCountry(
                request.registrationNumber(), request.registrationCountry())) {
            throw MerchantAlreadyExistsException.withRegistration(
                    request.registrationNumber(), request.registrationCountry());
        }

        var merchant = Merchant.createNew(
                request.legalName(),
                request.tradingName(),
                request.registrationNumber(),
                request.registrationCountry(),
                EntityType.valueOf(request.entityType()),
                request.websiteUrl(),
                request.primaryCurrency(),
                request.primaryContactEmail(),
                request.primaryContactName(),
                responseMapper.toBusinessAddress(request.registeredAddress()),
                responseMapper.toBeneficialOwners(request.beneficialOwners()),
                request.requestedCorridors()
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

        return responseMapper.toApplicationResponse(saved);
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
    public KybStatusResponse getKybStatus(UUID merchantId) {
        var merchant = findOrThrow(merchantId);
        var kybResult = kybProvider.getResult("merchant-" + merchantId);
        if (kybResult.isPresent()) {
            var kyb = kybResult.get();
            return new KybStatusResponse(
                    kyb.kybId(), kyb.status().name(), kyb.provider(),
                    kyb.providerRef(), kyb.initiatedAt(), kyb.completedAt(),
                    kyb.riskSignals());
        }
        return new KybStatusResponse(
                null, merchant.getKybStatus().name(), null,
                null, null, null, null);
    }

    @Transactional
    public MerchantResponse activate(UUID merchantId, ActivateMerchantRequest request) {
        var merchant = findOrThrow(merchantId);
        activationPolicy.validate(merchant);
        merchant.activate(request.approvedBy(), request.scopes());
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

        return responseMapper.toMerchantResponse(saved);
    }

    @Transactional
    public void suspend(UUID merchantId, SuspendMerchantRequest request) {
        var merchant = findOrThrow(merchantId);
        merchant.suspend();
        merchantRepository.save(merchant);
        log.info("Merchant suspended merchantId={} reason={}", merchantId, request.reason());

        eventPublisher.publish(MerchantSuspendedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantSuspendedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .correlationId(correlationId())
                .reason(request.reason())
                .suspendedBy(request.suspendedBy())
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
    public void close(UUID merchantId, CloseMerchantRequest request) {
        var merchant = findOrThrow(merchantId);
        merchant.close();
        merchantRepository.save(merchant);
        var reason = request != null ? request.reason() : null;
        log.info("Merchant closed merchantId={} reason={}", merchantId, reason);

        eventPublisher.publish(MerchantClosedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(MerchantClosedEvent.EVENT_TYPE)
                .merchantId(merchantId)
                .correlationId(correlationId())
                .reason(reason)
                .closedBy(request != null ? request.closedBy() : null)
                .closedAt(merchant.getClosedAt())
                .build());
    }

    @Transactional
    public MerchantResponse updateMerchant(UUID merchantId, UpdateMerchantRequest request) {
        var merchant = findOrThrow(merchantId);
        var builder = merchant.toBuilder();
        if (request.tradingName() != null) {
            builder.tradingName(request.tradingName());
        }
        if (request.websiteUrl() != null) {
            builder.websiteUrl(request.websiteUrl());
        }
        if (request.registeredAddress() != null) {
            builder.registeredAddress(responseMapper.toBusinessAddress(request.registeredAddress()));
        }
        var updated = builder.updatedAt(Instant.now()).build();
        var saved = merchantRepository.save(updated);
        log.info("Merchant updated merchantId={}", merchantId);
        return responseMapper.toMerchantResponse(saved);
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(UUID merchantId, DocumentUploadRequest request) {
        findOrThrow(merchantId);
        var uploadUrl = documentStore.generateUploadUrl(merchantId, request.documentType(), request.fileName());
        log.info("Document upload URL generated merchantId={} documentType={}", merchantId, request.documentType());
        return new DocumentUploadResponse(uploadUrl, Instant.now().plusSeconds(3600));
    }

    @Transactional
    public MerchantResponse updateRateLimitTier(UUID merchantId, UpdateRateLimitTierRequest request) {
        var merchant = findOrThrow(merchantId);
        var newTier = RateLimitTier.valueOf(request.newTier());
        merchant.upgradeRateLimitTier(newTier);
        var saved = merchantRepository.save(merchant);
        log.info("Rate limit tier updated merchantId={} newTier={}", merchantId, newTier);
        return responseMapper.toMerchantResponse(saved);
    }

    @Transactional
    public CorridorResponse approveCorridor(UUID merchantId, ApproveCorridorRequest request, UUID approvedBy) {
        var merchant = findOrThrow(merchantId);
        corridorEntitlementService.validate(merchant, request.sourceCountry(), request.targetCountry());

        var corridor = ApprovedCorridor.builder()
                .corridorId(UUID.randomUUID())
                .merchantId(merchantId)
                .sourceCountry(request.sourceCountry())
                .targetCountry(request.targetCountry())
                .currencies(request.currencies())
                .maxAmountUsd(request.maxAmountUsd())
                .approvedBy(approvedBy)
                .approvedAt(Instant.now())
                .expiresAt(request.expiresAt())
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

        return responseMapper.toCorridorResponse(saved);
    }

    @Transactional(readOnly = true)
    public MerchantResponse findById(UUID merchantId) {
        return responseMapper.toMerchantResponse(findOrThrow(merchantId));
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
