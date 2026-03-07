package com.stablecoin.payments.compliance.fixtures;

import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.Money;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.model.TransmissionStatus;
import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;
import com.stablecoin.payments.compliance.domain.model.TravelRuleProtocol;
import com.stablecoin.payments.compliance.domain.model.VaspInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ComplianceCheckFixtures {

    public static final Money SOURCE_AMOUNT = new Money(new BigDecimal("1000.00"), "USD");

    private ComplianceCheckFixtures() {}

    public static ComplianceCheck aPendingCheck() {
        return ComplianceCheck.initiate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SOURCE_AMOUNT, "US", "DE", "EUR");
    }

    public static KycResult aKycResult(UUID checkId) {
        return KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(checkId)
                .senderKycTier(KycTier.KYC_TIER_2)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("onfido")
                .providerRef("ref-kyc-" + UUID.randomUUID())
                .checkedAt(Instant.now())
                .build();
    }

    public static SanctionsResult aSanctionsClearResult(UUID checkId) {
        return SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .checkId(checkId)
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(false)
                .recipientHit(false)
                .listsChecked(List.of("OFAC", "EU", "UN"))
                .provider("chainalysis")
                .providerRef("ref-sanctions-" + UUID.randomUUID())
                .screenedAt(Instant.now())
                .build();
    }

    public static AmlResult anAmlClearResult(UUID checkId) {
        return AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .checkId(checkId)
                .flagged(false)
                .provider("chainalysis")
                .providerRef("ref-aml-" + UUID.randomUUID())
                .screenedAt(Instant.now())
                .build();
    }

    public static TravelRulePackage aTravelRulePackage(UUID checkId) {
        return TravelRulePackage.builder()
                .packageId(UUID.randomUUID())
                .checkId(checkId)
                .originatorVasp(new VaspInfo("vasp-1", "StableBridge US", "US", "did:web:stablebridge.us"))
                .beneficiaryVasp(new VaspInfo("vasp-2", "StableBridge DE", "DE", "did:web:stablebridge.de"))
                .originatorData("{\"name\":\"John Doe\"}")
                .beneficiaryData("{\"name\":\"Hans Mueller\"}")
                .protocol(TravelRuleProtocol.IVMS101)
                .transmissionStatus(TransmissionStatus.PENDING)
                .build();
    }
}
