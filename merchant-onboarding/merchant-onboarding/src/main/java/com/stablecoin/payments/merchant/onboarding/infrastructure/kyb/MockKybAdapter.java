package com.stablecoin.payments.merchant.onboarding.infrastructure.kyb;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.KybProvider;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.DocumentType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybVerification;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MockKybAdapter implements KybProvider {

    private final Map<String, KybVerification> store = new ConcurrentHashMap<>();

    @Override
    public KybVerification submit(UUID merchantId, String legalName, String registrationNumber, String country) {
        log.info("[MOCK-KYB] Submitting KYB for merchant={} legalName={}", merchantId, legalName);
        var kyb = KybVerification.builder()
                .kybId(UUID.randomUUID())
                .merchantId(merchantId)
                .provider("mock-onfido")
                .providerRef("mock-ref-" + UUID.randomUUID())
                .status(KybStatus.IN_PROGRESS)
                .riskSignals(Map.of("mock", true))
                .documentsRequired(List.of(DocumentType.CERTIFICATE_OF_INCORPORATION, DocumentType.PROOF_OF_ADDRESS))
                .initiatedAt(Instant.now())
                .build();
        store.put(kyb.providerRef(), kyb);
        return kyb;
    }

    @Override
    public Optional<KybVerification> getResult(String providerRef) {
        log.debug("[MOCK-KYB] Getting result for providerRef={}", providerRef);
        return Optional.ofNullable(store.get(providerRef));
    }

    @Override
    public KybVerification handleWebhook(Map<String, Object> payload) {
        var providerRef = (String) payload.get("providerRef");
        log.info("[MOCK-KYB] Handling webhook for providerRef={}", providerRef);
        var existing = store.get(providerRef);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown providerRef: " + providerRef);
        }
        var completed = KybVerification.builder()
                .kybId(existing.kybId())
                .merchantId(existing.merchantId())
                .provider(existing.provider())
                .providerRef(providerRef)
                .status(KybStatus.PASSED)
                .riskSignals(Map.of("mock", true, "score", 15))
                .documentsRequired(existing.documentsRequired())
                .initiatedAt(existing.initiatedAt())
                .completedAt(Instant.now())
                .build();
        store.put(providerRef, completed);
        return completed;
    }

    @Override
    public List<DocumentType> getRequiredDocuments(String country, String entityType) {
        return List.of(DocumentType.CERTIFICATE_OF_INCORPORATION, DocumentType.PROOF_OF_ADDRESS);
    }
}
