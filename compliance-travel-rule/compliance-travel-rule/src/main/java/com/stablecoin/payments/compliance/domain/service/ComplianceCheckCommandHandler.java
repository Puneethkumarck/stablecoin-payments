package com.stablecoin.payments.compliance.domain.service;

import com.stablecoin.payments.compliance.domain.event.ComplianceCheckFailed;
import com.stablecoin.payments.compliance.domain.event.ComplianceCheckPassed;
import com.stablecoin.payments.compliance.domain.event.SanctionsHitEvent;
import com.stablecoin.payments.compliance.domain.exception.CheckNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.CustomerNotFoundException;
import com.stablecoin.payments.compliance.domain.exception.DuplicatePaymentException;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.CustomerRiskProfile;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain command handler that orchestrates the compliance check pipeline.
 * <p>
 * Coordinates KYC, sanctions, AML, risk scoring, and travel rule sub-checks
 * through provider ports, persists the aggregate via the repository port,
 * and publishes domain events via the event publisher port.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ComplianceCheckCommandHandler {

    private final ComplianceCheckRepository checkRepository;
    private final CustomerRiskProfileRepository profileRepository;
    private final KycProvider kycProvider;
    private final SanctionsProvider sanctionsProvider;
    private final AmlProvider amlProvider;
    private final TravelRuleProvider travelRuleProvider;
    private final ComplianceCheckService complianceCheckService;
    private final RiskScoringService riskScoringService;
    private final EventPublisher<Object> eventPublisher;

    /**
     * Initiates a new compliance check for a payment.
     *
     * @throws DuplicatePaymentException if a check already exists for the payment
     */
    public ComplianceCheck initiateCheck(UUID paymentId, UUID senderId, UUID recipientId,
                                         Money sourceAmount, String sourceCountry,
                                         String targetCountry, String targetCurrency) {
        log.info("Initiating compliance check for payment={}", paymentId);

        checkRepository.findByPaymentId(paymentId)
                .ifPresent(existing -> {
                    throw new DuplicatePaymentException(paymentId);
                });

        var check = complianceCheckService.initiate(
                paymentId, senderId, recipientId,
                sourceAmount, sourceCountry, targetCountry, targetCurrency);

        check = runPipeline(check);

        var saved = checkRepository.save(check);
        publishEvents(saved);

        log.info("Compliance check completed for payment={}, status={}, result={}",
                saved.paymentId(), saved.status(), saved.overallResult());

        return saved;
    }

    /**
     * Retrieves a compliance check by its ID.
     *
     * @throws CheckNotFoundException if no check exists with the given ID
     */
    @Transactional(readOnly = true)
    public ComplianceCheck getCheck(UUID checkId) {
        return checkRepository.findById(checkId)
                .orElseThrow(() -> new CheckNotFoundException(checkId));
    }

    /**
     * Retrieves a customer risk profile by customer ID.
     *
     * @throws CustomerNotFoundException if no profile exists for the customer
     */
    @Transactional(readOnly = true)
    public CustomerRiskProfile getCustomerRiskProfile(UUID customerId) {
        return profileRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
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
