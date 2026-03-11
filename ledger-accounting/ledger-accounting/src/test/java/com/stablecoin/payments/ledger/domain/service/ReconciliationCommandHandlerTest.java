package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationProperties;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static com.stablecoin.payments.ledger.fixtures.TestUtils.eqIgnoring;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class ReconciliationCommandHandlerTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private ReconciliationRepository reconciliationRepository;
    private ReconciliationLegRepository legRepository;
    private ReconciliationCommandHandler handler;

    @BeforeEach
    void setUp() {
        reconciliationRepository = mock(ReconciliationRepository.class);
        legRepository = mock(ReconciliationLegRepository.class);
        ReconciliationProperties properties = mock(ReconciliationProperties.class);
        given(properties.tolerance()).willReturn(TOLERANCE);

        handler = new ReconciliationCommandHandler(
                reconciliationRepository, legRepository, properties, FIXED_CLOCK);
    }

    @Nested
    @DisplayName("createRecord")
    class CreateRecord {

        @Test
        @DisplayName("should create new PENDING record when none exists")
        void createsNewRecord() {
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
            var expectedRecord = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.save(eqIgnoring(expectedRecord, "recId", "createdAt", "updatedAt")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.createRecord(PAYMENT_ID);

            then(reconciliationRepository).should()
                    .save(eqIgnoring(expectedRecord, "recId", "createdAt", "updatedAt"));
        }

        @Test
        @DisplayName("should return existing record when already created")
        void returnsExistingRecord() {
            var existingRecord = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(existingRecord));

            handler.createRecord(PAYMENT_ID);

            then(reconciliationRepository).should(never())
                    .save(eqIgnoring(existingRecord, "recId", "createdAt", "updatedAt"));
        }
    }

    @Nested
    @DisplayName("recordLeg")
    class RecordLeg {

        @Test
        @DisplayName("should save leg and update record")
        void savesLeg() {
            var record = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));
            given(reconciliationRepository.save(eqIgnoring(record, "recId", "status", "legs", "createdAt", "updatedAt")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.recordLeg(PAYMENT_ID, ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD", SOURCE_EVENT_ID);

            then(legRepository).should().save(eqIgnoring(
                    new ReconciliationLeg(UUID.randomUUID(), record.recId(),
                            ReconciliationLegType.FIAT_IN,
                            new BigDecimal("10000.00"), "USD",
                            SOURCE_EVENT_ID, NOW),
                    "legId"));
        }

        @Test
        @DisplayName("should skip duplicate leg (idempotent)")
        void skipsDuplicateLeg() {
            var leg = new ReconciliationLeg(UUID.randomUUID(), UUID.randomUUID(),
                    ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD", SOURCE_EVENT_ID, NOW);
            var record = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE).addLeg(leg);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));

            handler.recordLeg(PAYMENT_ID, ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD", SOURCE_EVENT_ID);

            then(legRepository).should(never()).save(eqIgnoring(leg, "legId"));
        }

        @Test
        @DisplayName("should create record if not exists then add leg")
        void createsRecordIfNeeded() {
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());
            var newRecord = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.save(eqIgnoring(newRecord, "recId", "createdAt", "updatedAt")))
                    .willReturn(newRecord);

            handler.recordLeg(PAYMENT_ID, ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD", SOURCE_EVENT_ID);

            then(legRepository).should().save(eqIgnoring(
                    new ReconciliationLeg(UUID.randomUUID(), newRecord.recId(),
                            ReconciliationLegType.FIAT_IN,
                            new BigDecimal("10000.00"), "USD",
                            SOURCE_EVENT_ID, NOW),
                    "legId"));
        }
    }

    @Nested
    @DisplayName("findLeg")
    class FindLeg {

        @Test
        @DisplayName("should return leg when present")
        void returnsLeg() {
            var leg = new ReconciliationLeg(UUID.randomUUID(), UUID.randomUUID(),
                    ReconciliationLegType.FX_RATE,
                    new BigDecimal("30.00"), "USD", SOURCE_EVENT_ID, NOW);
            var record = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE).addLeg(leg);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));

            var result = handler.findLeg(PAYMENT_ID, ReconciliationLegType.FX_RATE);

            assertThat(result).isPresent();
            assertThat(result.get()).usingRecursiveComparison().isEqualTo(leg);
        }

        @Test
        @DisplayName("should return empty when leg not present")
        void returnsEmptyWhenMissing() {
            var record = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));

            var result = handler.findLeg(PAYMENT_ID, ReconciliationLegType.FX_RATE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("markDiscrepancy")
    class MarkDiscrepancy {

        @Test
        @DisplayName("should mark record as DISCREPANCY")
        void marksDiscrepancy() {
            var record = ReconciliationRecord.create(PAYMENT_ID, TOLERANCE);
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));
            given(reconciliationRepository.save(eqIgnoring(record.markDiscrepancy(), "recId", "createdAt", "updatedAt")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.markDiscrepancy(PAYMENT_ID);

            then(reconciliationRepository).should()
                    .save(eqIgnoring(record.markDiscrepancy(), "recId", "createdAt", "updatedAt"));
        }

        @Test
        @DisplayName("should skip when no record exists")
        void skipsWhenNoRecord() {
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());

            handler.markDiscrepancy(PAYMENT_ID);

            then(reconciliationRepository).should(never())
                    .save(eqIgnoring(ReconciliationRecord.create(PAYMENT_ID, TOLERANCE), "recId", "createdAt", "updatedAt"));
        }
    }
}
