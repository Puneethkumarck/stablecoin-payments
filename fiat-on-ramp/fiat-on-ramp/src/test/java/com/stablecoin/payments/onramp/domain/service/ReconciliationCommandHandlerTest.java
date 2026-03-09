package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.ReconciliationDiscrepancyEvent;
import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.ReconciliationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.aCollectedOrderWithDifferentAmount;
import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.aCollectedOrderWithinTolerance;
import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.aMatchedReconciliation;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationCommandHandler")
class ReconciliationCommandHandlerTest {

    @Mock private ReconciliationRecordRepository reconciliationRecordRepository;
    @Mock private CollectionEventPublisher eventPublisher;

    private ReconciliationCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReconciliationCommandHandler(reconciliationRecordRepository, eventPublisher);
    }

    @Nested
    @DisplayName("reconcile")
    class Reconcile {

        @Test
        @DisplayName("should create MATCHED record when amounts match exactly")
        void shouldCreateMatchedRecordWhenAmountsMatchExactly() {
            // given
            var order = aCollectedOrder();
            var expectedRecord = ReconciliationRecord.reconcile(
                    order.collectionId(),
                    order.psp().pspName(),
                    order.pspReference(),
                    order.amount().amount(),
                    order.collectedAmount().amount(),
                    order.amount().currency());

            given(reconciliationRecordRepository.existsByCollectionId(order.collectionId()))
                    .willReturn(false);
            given(reconciliationRecordRepository.save(
                    eqIgnoring(expectedRecord, "reconciliationId")))
                    .willReturn(expectedRecord);

            // when
            handler.reconcile(order);

            // then
            then(reconciliationRecordRepository).should().save(
                    eqIgnoring(expectedRecord, "reconciliationId"));
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should create MATCHED record when discrepancy is within tolerance (0.01)")
        void shouldCreateMatchedRecordWhenDiscrepancyWithinTolerance() {
            // given — order with expected 1000.00 and collected 999.995 (diff = 0.005 < 0.01)
            var order = aCollectedOrderWithinTolerance();

            var expectedRecord = ReconciliationRecord.reconcile(
                    order.collectionId(),
                    order.psp().pspName(),
                    order.pspReference(),
                    order.amount().amount(),
                    order.collectedAmount().amount(),
                    order.amount().currency());

            given(reconciliationRecordRepository.existsByCollectionId(order.collectionId()))
                    .willReturn(false);
            given(reconciliationRecordRepository.save(
                    eqIgnoring(expectedRecord, "reconciliationId")))
                    .willReturn(expectedRecord);

            // when
            handler.reconcile(order);

            // then
            then(reconciliationRecordRepository).should().save(
                    eqIgnoring(expectedRecord, "reconciliationId"));
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should create DISCREPANCY record when amounts differ by more than 0.01")
        void shouldCreateDiscrepancyRecordWhenAmountsDiffer() {
            // given
            var order = aCollectedOrderWithDifferentAmount();
            var expectedRecord = ReconciliationRecord.reconcile(
                    order.collectionId(),
                    order.psp().pspName(),
                    order.pspReference(),
                    order.amount().amount(),
                    order.collectedAmount().amount(),
                    order.amount().currency());

            var expectedEvent = new ReconciliationDiscrepancyEvent(
                    expectedRecord.reconciliationId(),
                    order.collectionId(),
                    order.paymentId(),
                    order.correlationId(),
                    order.amount().amount(),
                    order.collectedAmount().amount(),
                    new BigDecimal("50.00"),
                    order.amount().currency(),
                    null);

            given(reconciliationRecordRepository.existsByCollectionId(order.collectionId()))
                    .willReturn(false);
            given(reconciliationRecordRepository.save(
                    eqIgnoring(expectedRecord, "reconciliationId")))
                    .willReturn(expectedRecord);

            // when
            handler.reconcile(order);

            // then
            then(reconciliationRecordRepository).should().save(
                    eqIgnoring(expectedRecord, "reconciliationId"));
            then(eventPublisher).should().publish(
                    eqIgnoring(expectedEvent, "reconciliationId"));
        }

        @Test
        @DisplayName("should publish ReconciliationDiscrepancyEvent on discrepancy")
        void shouldPublishAlertOnDiscrepancy() {
            // given
            var order = aCollectedOrderWithDifferentAmount();
            var expectedRecord = ReconciliationRecord.reconcile(
                    order.collectionId(),
                    order.psp().pspName(),
                    order.pspReference(),
                    order.amount().amount(),
                    order.collectedAmount().amount(),
                    order.amount().currency());

            given(reconciliationRecordRepository.existsByCollectionId(order.collectionId()))
                    .willReturn(false);
            given(reconciliationRecordRepository.save(
                    eqIgnoring(expectedRecord, "reconciliationId")))
                    .willReturn(expectedRecord);

            // when
            handler.reconcile(order);

            // then
            then(eventPublisher).should().publish(eqIgnoringTimestamps(
                    new ReconciliationDiscrepancyEvent(
                            expectedRecord.reconciliationId(),
                            order.collectionId(),
                            order.paymentId(),
                            order.correlationId(),
                            order.amount().amount(),
                            order.collectedAmount().amount(),
                            new BigDecimal("50.00"),
                            order.amount().currency(),
                            null)));
        }

        @Test
        @DisplayName("should skip reconciliation when record already exists for collectionId")
        void shouldSkipWhenAlreadyReconciled() {
            // given
            var order = aCollectedOrder();
            var existingRecord = aMatchedReconciliation();

            given(reconciliationRecordRepository.existsByCollectionId(order.collectionId()))
                    .willReturn(true);
            given(reconciliationRecordRepository.findByCollectionId(order.collectionId()))
                    .willReturn(Optional.of(existingRecord));

            // when
            handler.reconcile(order);

            // then
            then(reconciliationRecordRepository).should(never()).save(eqIgnoringTimestamps(existingRecord));
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }
}
