package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aChainTransferredLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatInLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatOutLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFxRateLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aPendingRecord;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinMintedLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinRedeemedLeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReconciliationLegPersistenceAdapter IT")
class ReconciliationLegPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private ReconciliationLegRepository legAdapter;

    @Autowired
    private ReconciliationRepository recordAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save leg and retrieve by rec id")
    void shouldSaveAndRetrieveByRecId() {
        recordAdapter.save(aPendingRecord());
        var leg = aFiatInLeg();
        var saved = legAdapter.save(leg);

        assertThat(legAdapter.findByRecId(saved.recId()))
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should save multiple legs and retrieve all by rec id")
    void shouldSaveMultipleLegsAndRetrieveAll() {
        recordAdapter.save(aPendingRecord());
        legAdapter.save(aFiatInLeg());
        legAdapter.save(aStablecoinMintedLeg());
        legAdapter.save(aChainTransferredLeg());

        assertThat(legAdapter.findByRecId(aPendingRecord().recId())).hasSize(3);
    }

    // ── All 6 Leg Types ──────────────────────────────────────────────────

    @Test
    @DisplayName("should persist all 6 leg types correctly")
    void shouldPersistAll6LegTypes() {
        recordAdapter.save(aPendingRecord());
        legAdapter.save(aFiatInLeg());
        legAdapter.save(aStablecoinMintedLeg());
        legAdapter.save(aChainTransferredLeg());
        legAdapter.save(aStablecoinRedeemedLeg());
        legAdapter.save(aFiatOutLeg());
        legAdapter.save(aFxRateLeg());

        assertThat(legAdapter.findByRecId(aPendingRecord().recId()))
                .hasSize(6)
                .extracting(ReconciliationLeg::legType)
                .containsExactlyInAnyOrder(
                        ReconciliationLegType.FIAT_IN,
                        ReconciliationLegType.STABLECOIN_MINTED,
                        ReconciliationLegType.CHAIN_TRANSFERRED,
                        ReconciliationLegType.STABLECOIN_REDEEMED,
                        ReconciliationLegType.FIAT_OUT,
                        ReconciliationLegType.FX_RATE
                );
    }

    // ── Constraints ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique (rec_id, leg_type) constraint")
    void shouldEnforceUniqueRecIdLegTypeConstraint() {
        recordAdapter.save(aPendingRecord());
        legAdapter.save(aFiatInLeg());

        var duplicateLeg = new ReconciliationLeg(
                UUID.randomUUID(),
                aPendingRecord().recId(),
                ReconciliationLegType.FIAT_IN,
                new BigDecimal("20000.00"),
                "USD",
                UUID.randomUUID(),
                Instant.now()
        );

        assertThatThrownBy(() -> legAdapter.save(duplicateLeg))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should return empty list for non-existent rec id")
    void shouldReturnEmptyForNonExistentRecId() {
        assertThat(legAdapter.findByRecId(UUID.randomUUID())).isEmpty();
    }
}
