package com.stablecoin.payments.compliance.application.service;

import com.stablecoin.payments.compliance.api.request.InitiateComplianceCheckRequest;
import com.stablecoin.payments.compliance.api.response.ComplianceCheckResponse;
import com.stablecoin.payments.compliance.api.response.CustomerRiskProfileResponse;
import com.stablecoin.payments.compliance.application.mapper.ComplianceCheckResponseMapper;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckFailed;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckPassed;
import com.stablecoin.payments.compliance.domain.event.SanctionsHitEvent;
import com.stablecoin.payments.compliance.domain.exception.CheckNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.CustomerNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.DuplicatePaymentException;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.RiskScoringContext;
import com.stablecoin.payments.compliance.domain.model.TransmissionStatus;
import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;
import com.stablecoin.payments.compliance.domain.model.TravelRuleProtocol;
import com.stablecoin.payments.compliance.domain.model.VaspInfo;
import com.stablecoin.payments.compliance.domain.port.AmlProvider;
import com.stablecoin.payments.compliance.domain.port.ComplianceCheckRepository;
import com.stablecoin.payments.compliance.domain.port.CustomerRiskProfileRepository;
import com.stablecoin.payments.compliance.domain.port.EventPublisher;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import com.stablecoin.payments.compliance.domain.port.TravelRuleProvider;
import com.stablecoin.payments.compliance.domain.service.ComplianceCheckService;
import com.stablecoin.payments.compliance.domain.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckApplicationService {

    private final ComplianceCheckRepository checkRepository;
    private final CustomerRiskProfileRepository profileRepository;
    private final KycProvider kycProvider;
    private final SanctionsProvider sanctionsProvider;
    private final AmlProvider amlProvider;
    private final TravelRuleProvider travelRuleProvider;
    private final ComplianceCheckService complianceCheckService;
    private final RiskScoringService riskScoringService;
    private final EventPublisher<Object> eventPublisher;
    private final ComplianceCheckResponseMapper responseMapper;

    @Transactional
    public ComplianceCheckResponse initiateCheck(InitiateComplianceCheckRequest request) {
        log.info("Initiating compliance check for payment={}", request.paymentId());

        checkRepository.findByPaymentId(request.paymentId())
                .ifPresent(existing -> {
                    throw new DuplicatePaymentException(request.paymentId());
                });

        var check = complianceCheckService.initiate(
                request.paymentId(),
                request.senderId(),
                request.recipientId(),
                new Money(request.amount(), request.currency()),
                request.sourceCountry(),
                request.targetCountry(),
                request.targetCurrency());

        check = runPipeline(check);

        var saved = checkRepository.save(check);
        publishEvents(saved);

        log.info("Compliance check completed for payment={}, status={}, result={}",
                saved.paymentId(), saved.status(), saved.overallResult());

        return responseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ComplianceCheckResponse getCheck(UUID checkId) {
        var check = checkRepository.findById(checkId)
                .orElseThrow(() -> new CheckNotFoundException(checkId));
        return responseMapper.toResponse(check);
    }

    @Transactional(readOnly = true)
    public CustomerRiskProfileResponse getCustomerRiskProfile(UUID customerId) {
        var profile = profileRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return responseMapper.toResponse(profile);
    }

    private ComplianceCheck runPipeline(ComplianceCheck check) {
        // Step 1: KYC verification
        var kycResult = kycProvider.verify(check.senderId(), check.recipientId());
        check = complianceCheckService.recordKycResult(check, kycResult);
        if (check.isTerminal()) {
            return check;
        }

        // Step 2: Sanctions screening
        var sanctionsResult = sanctionsProvider.screen(check.senderId(), check.recipientId());
        check = complianceCheckService.recordSanctionsResult(check, sanctionsResult);
        if (check.isTerminal()) {
            return check;
        }

        // Step 3: AML analysis
        var amlResult = amlProvider.analyze(check.senderId(), check.recipientId());
        check = complianceCheckService.recordAmlResult(check, amlResult);
        if (check.isTerminal()) {
            return check;
        }

        // Step 4: Risk scoring
        var profile = profileRepository.findByCustomerId(check.senderId()).orElse(null);
        var context = RiskScoringContext.builder()
                .check(check)
                .customerProfile(profile)
                .recentTransactionCount(0)
                .build();
        var riskScore = riskScoringService.calculateScore(context);
        check = complianceCheckService.recordRiskScore(check, riskScore);
        if (check.isTerminal()) {
            return check;
        }

        // Step 5: Travel rule packaging
        if (complianceCheckService.requiresTravelRule(check)) {
            check = handleTravelRule(check);
        } else {
            check = complianceCheckService.skipTravelRule(check);
        }

        return check;
    }

    private ComplianceCheck handleTravelRule(ComplianceCheck check) {
        var travelRulePackage = TravelRulePackage.builder()
                .packageId(UUID.randomUUID())
                .checkId(check.checkId())
                .originatorVasp(new VaspInfo("vasp-originator", "StableBridge", check.sourceCountry(), null))
                .beneficiaryVasp(new VaspInfo("vasp-beneficiary", "StableBridge", check.targetCountry(), null))
                .originatorData("{}")
                .beneficiaryData("{}")
                .protocol(TravelRuleProtocol.IVMS101)
                .transmissionStatus(TransmissionStatus.PENDING)
                .build();

        try {
            var protocolRef = travelRuleProvider.transmit(travelRulePackage);
            travelRulePackage = travelRulePackage.toBuilder()
                    .transmissionStatus(TransmissionStatus.TRANSMITTED)
                    .transmittedAt(Instant.now())
                    .protocolRef(protocolRef)
                    .build();
            return complianceCheckService.recordTravelRuleResult(check, travelRulePackage);
        } catch (Exception e) {
            log.error("Travel rule transmission failed for check={}: {}", check.checkId(), e.getMessage());
            return check.failTravelRule("Travel rule transmission failed: " + e.getMessage());
        }
    }

    private void publishEvents(ComplianceCheck check) {
        if (check.overallResult() == null) {
            return;
        }

        switch (check.overallResult()) {
            case PASSED -> eventPublisher.publish(new ComplianceCheckPassed(
                    check.checkId(),
                    check.paymentId(),
                    check.correlationId(),
                    check.riskScore() != null ? check.riskScore().score() : 0,
                    check.riskScore() != null ? check.riskScore().band().name() : null,
                    check.travelRulePackage() != null ? check.travelRulePackage().protocolRef() : null,
                    check.completedAt()));

            case FAILED -> eventPublisher.publish(new ComplianceCheckFailed(
                    check.checkId(),
                    check.paymentId(),
                    check.correlationId(),
                    check.errorMessage(),
                    check.errorCode(),
                    check.completedAt()));

            case SANCTIONS_HIT -> {
                eventPublisher.publish(new ComplianceCheckFailed(
                        check.checkId(),
                        check.paymentId(),
                        check.correlationId(),
                        check.errorMessage(),
                        check.errorCode(),
                        check.completedAt()));
                publishSanctionsHitEvent(check);
            }

            case MANUAL_REVIEW -> eventPublisher.publish(new ComplianceCheckFailed(
                    check.checkId(),
                    check.paymentId(),
                    check.correlationId(),
                    check.errorMessage(),
                    check.errorCode(),
                    check.completedAt()));
        }
    }

    private void publishSanctionsHitEvent(ComplianceCheck check) {
        var sanctions = check.sanctionsResult();
        if (sanctions == null) {
            return;
        }

        String hitParty;
        if (sanctions.senderHit() && sanctions.recipientHit()) {
            hitParty = "BOTH";
        } else if (sanctions.senderHit()) {
            hitParty = "SENDER";
        } else {
            hitParty = "RECIPIENT";
        }

        var listsChecked = sanctions.listsChecked();
        eventPublisher.publish(new SanctionsHitEvent(
                check.checkId(),
                check.paymentId(),
                check.correlationId(),
                hitParty,
                listsChecked != null && !listsChecked.isEmpty() ? listsChecked.getFirst() : "UNKNOWN",
                sanctions.hitDetails(),
                check.completedAt()));
    }
}
