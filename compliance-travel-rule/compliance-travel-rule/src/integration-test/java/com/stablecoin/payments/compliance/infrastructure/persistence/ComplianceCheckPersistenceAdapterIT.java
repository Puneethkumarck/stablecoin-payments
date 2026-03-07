package com.stablecoin.payments.compliance.infrastructure.persistence;

import com.stablecoin.payments.compliance.AbstractIntegrationTest;
import com.stablecoin.payments.compliance.domain.model.AmlResult;
import com.stablecoin.payments.compliance.domain.model.ComplianceCheck;
import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.model.RiskBand;
import com.stablecoin.payments.compliance.domain.model.RiskScore;
import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.model.TransmissionStatus;
import com.stablecoin.payments.compliance.domain.model.TravelRulePackage;
import com.stablecoin.payments.compliance.domain.model.TravelRuleProtocol;
import com.stablecoin.payments.compliance.domain.model.VaspInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.SOURCE_AMOUNT;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aKycResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aPendingCheck;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aSanctionsClearResult;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.aTravelRulePackage;
import static com.stablecoin.payments.compliance.fixtures.ComplianceCheckFixtures.anAmlClearResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ComplianceCheckPersistenceAdapter IT")
class ComplianceCheckPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private ComplianceCheckPersistenceAdapter adapter;

    // ── Basic CRUD ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should save and find pending check by id")
    void shouldSaveAndFindPendingCheckById() {
        var check = aPendingCheck();
        var saved = adapter.save(check);

        assertThat(adapter.findById(saved.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt")
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find check by payment id")
    void shouldFindByPaymentId() {
        var check = aPendingCheck();
        var saved = adapter.save(check);

        assertThat(adapter.findByPaymentId(check.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt")
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when payment id not found")
    void shouldReturnEmptyWhenPaymentIdNotFound() {
        assertThat(adapter.findByPaymentId(UUID.randomUUID())).isEmpty();
    }

    // ── Sub-entity persistence ──────────────────────────────────────────

    @Test
    @DisplayName("should save check with KYC result and verify JSONB raw response default")
    void shouldSaveCheckWithKycResult() {
        var check = aPendingCheck();
        check = adapter.save(check);
        check = check.startKyc();
        var kycResult = KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .checkId(check.checkId())
                .senderKycTier(KycTier.KYC_TIER_2)
                .senderStatus(KycStatus.VERIFIED)
                .recipientStatus(KycStatus.VERIFIED)
                .provider("onfido")
                .providerRef("ref-kyc-123")
                .checkedAt(Instant.now())
                .build();
        check = check.passKyc(kycResult);
        adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt")
                .isEqualTo(check);
    }

    @Test
    @DisplayName("should save sanctions result with JSONB hit details and text[] lists")
    void shouldSaveSanctionsResultWithJsonbAndTextArray() {
        var check = progressToSanctionsScreening();
        var sanctionsResult = SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .checkId(check.checkId())
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(true)
                .recipientHit(false)
                .hitDetails("{\"list\":\"OFAC\",\"matchScore\":0.95}")
                .listsChecked(List.of("OFAC", "EU", "UN"))
                .provider("chainalysis")
                .providerRef("ref-sanctions-456")
                .screenedAt(Instant.now())
                .build();
        check = check.sanctionsHitDetected(sanctionsResult);
        adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt", "sanctionsResult.hitDetails")
                .isEqualTo(check);
    }

    @Test
    @DisplayName("should save AML result with JSONB chain analysis and text[] flag reasons")
    void shouldSaveAmlResultWithJsonbAndTextArray() {
        var check = progressToAmlScreening();
        var amlResult = AmlResult.builder()
                .amlResultId(UUID.randomUUID())
                .checkId(check.checkId())
                .flagged(true)
                .flagReasons(List.of("high_risk_jurisdiction", "unusual_pattern"))
                .chainAnalysis("{\"risk\":\"high\",\"exposure\":{\"darknet\":0.01}}")
                .provider("chainalysis")
                .providerRef("ref-aml-789")
                .screenedAt(Instant.now())
                .build();
        check = check.amlFlagged(amlResult);
        adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt", "amlResult.chainAnalysis")
                .isEqualTo(check);
    }

    @Test
    @DisplayName("should save travel rule package with JSONB vasp info and BYTEA data")
    void shouldSaveTravelRulePackageWithJsonbAndBytea() {
        var check = progressToTravelRulePackaging();
        var travelRule = TravelRulePackage.builder()
                .packageId(UUID.randomUUID())
                .checkId(check.checkId())
                .originatorVasp(new VaspInfo("vasp-1", "StableBridge US", "US", "did:web:stablebridge.us"))
                .beneficiaryVasp(new VaspInfo("vasp-2", "StableBridge DE", "DE", "did:web:stablebridge.de"))
                .originatorData("{\"name\":\"John Doe\",\"account\":\"US123\"}")
                .beneficiaryData("{\"name\":\"Hans Mueller\",\"account\":\"DE456\"}")
                .protocol(TravelRuleProtocol.IVMS101)
                .transmissionStatus(TransmissionStatus.TRANSMITTED)
                .transmittedAt(Instant.now())
                .protocolRef("trisa-ref-001")
                .build();
        check = check.completeTravelRule(travelRule);
        adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt")
                .isEqualTo(check);
    }

    // ── Full pipeline ───────────────────────────────────────────────────

    @Test
    @DisplayName("should save full compliance pipeline from PENDING to PASSED with risk factors")
    void shouldSaveFullCompliancePipeline() {
        var check = aPendingCheck();
        check = adapter.save(check);

        check = check.startKyc();
        check = check.passKyc(aKycResult(check.checkId()));
        check = adapter.save(check);

        check = check.sanctionsClear(aSanctionsClearResult(check.checkId()));
        check = adapter.save(check);

        check = check.amlClear(anAmlClearResult(check.checkId()));
        check = adapter.save(check);

        check = check.riskScored(new RiskScore(25, RiskBand.LOW, List.of("low_amount", "known_corridor")));
        check = adapter.save(check);

        check = check.completeTravelRule(aTravelRulePackage(check.checkId()));
        check = adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt", "completedAt")
                .isEqualTo(check);
    }

    // ── Update & constraints ────────────────────────────────────────────

    @Test
    @DisplayName("should update existing check status via adapter upsert path")
    void shouldUpdateExistingCheckStatus() {
        var check = aPendingCheck();
        check = adapter.save(check);

        check = check.startKyc();
        adapter.save(check);

        assertThat(adapter.findById(check.checkId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("createdAt")
                .isEqualTo(check);
    }

    @Test
    @DisplayName("should enforce unique constraint on payment_id")
    void shouldEnforceUniquePaymentIdConstraint() {
        var paymentId = UUID.randomUUID();
        var check1 = ComplianceCheck.initiate(
                paymentId, UUID.randomUUID(), UUID.randomUUID(),
                SOURCE_AMOUNT, "US", "DE", "EUR");
        adapter.save(check1);

        var check2 = ComplianceCheck.initiate(
                paymentId, UUID.randomUUID(), UUID.randomUUID(),
                SOURCE_AMOUNT, "US", "DE", "EUR");

        assertThatThrownBy(() -> adapter.save(check2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ComplianceCheck progressToSanctionsScreening() {
        var check = aPendingCheck();
        check = adapter.save(check);
        check = check.startKyc();
        check = check.passKyc(aKycResult(check.checkId()));
        return adapter.save(check);
    }

    private ComplianceCheck progressToAmlScreening() {
        var check = progressToSanctionsScreening();
        check = check.sanctionsClear(aSanctionsClearResult(check.checkId()));
        return adapter.save(check);
    }

    private ComplianceCheck progressToTravelRulePackaging() {
        var check = progressToAmlScreening();
        check = check.amlClear(anAmlClearResult(check.checkId()));
        check = adapter.save(check);
        check = check.riskScored(new RiskScore(25, RiskBand.LOW, List.of("low_amount")));
        return adapter.save(check);
    }
}
