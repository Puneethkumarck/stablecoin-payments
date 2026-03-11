package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.DEFAULT_TOLERANCE;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatInLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aPendingRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReconciliationPersistenceAdapter IT")
class ReconciliationPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private ReconciliationRepository adapter;

    @Autowired
    private ReconciliationLegRepository legAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save record and retrieve by payment id")
    void shouldSaveAndRetrieveByPaymentId() {
        var record = aPendingRecord();
        var saved = adapter.save(record);

        assertThat(adapter.findByPaymentId(saved.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should save record and retrieve by id")
    void shouldSaveAndRetrieveById() {
        var record = aPendingRecord();
        var saved = adapter.save(record);

        assertThat(adapter.findById(saved.recId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── Status Updates ───────────────────────────────────────────────────

    @Test
    @DisplayName("should update status from PENDING to PARTIAL")
    void shouldUpdateStatusFromPendingToPartial() {
        var saved = adapter.save(aPendingRecord());
        var leg = aFiatInLeg();
        legAdapter.save(leg);
        var updated = saved.addLeg(leg);
        var savedUpdated = adapter.save(updated);

        assertThat(adapter.findById(savedUpdated.recId())).isPresent().get()
                .extracting(ReconciliationRecord::status)
                .isEqualTo(ReconciliationStatus.PARTIAL);
    }

    @Test
    @DisplayName("should find records by status")
    void shouldFindByStatus() {
        adapter.save(aPendingRecord());

        assertThat(adapter.findByStatus(ReconciliationStatus.PENDING)).hasSize(1);
        assertThat(adapter.findByStatus(ReconciliationStatus.RECONCILED)).isEmpty();
    }

    // ── Record with Legs ─────────────────────────────────────────────────

    @Test
    @DisplayName("should return record with legs populated after saving legs separately")
    void shouldReturnRecordWithLegsPopulated() {
        var saved = adapter.save(aPendingRecord());
        legAdapter.save(aFiatInLeg());

        assertThat(adapter.findById(saved.recId()))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.legs()).hasSize(1));
    }

    // ── Constraints ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique payment_id constraint")
    void shouldEnforceUniquePaymentIdConstraint() {
        adapter.save(aPendingRecord());

        var duplicate = ReconciliationRecord.create(aPendingRecord().paymentId(), DEFAULT_TOLERANCE);

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should return empty for non-existent payment id")
    void shouldReturnEmptyForNonExistentPaymentId() {
        assertThat(adapter.findByPaymentId(UUID.randomUUID())).isEmpty();
    }
}
