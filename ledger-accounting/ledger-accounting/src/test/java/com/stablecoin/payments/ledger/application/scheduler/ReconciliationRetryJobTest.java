package com.stablecoin.payments.ledger.application.scheduler;

import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import com.stablecoin.payments.ledger.domain.service.ReconciliationCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aRecordWithAllRequiredLegs;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class ReconciliationRetryJobTest {

    private ReconciliationRepository reconciliationRepository;
    private ReconciliationCommandHandler reconciliationCommandHandler;
    private ReconciliationRetryJob job;

    @BeforeEach
    void setUp() {
        reconciliationRepository = mock(ReconciliationRepository.class);
        reconciliationCommandHandler = mock(ReconciliationCommandHandler.class);
        job = new ReconciliationRetryJob(reconciliationRepository, reconciliationCommandHandler);
    }

    @Test
    @DisplayName("should finalize PARTIAL record with all legs present")
    void finalizesPartialRecordWithAllLegs() {
        var record = aRecordWithAllRequiredLegs();
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PENDING)).willReturn(List.of());
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PARTIAL)).willReturn(List.of(record));
        var finalized = record.finalize(BigDecimal.ZERO);
        given(reconciliationCommandHandler.finalizeReconciliation(PAYMENT_ID))
                .willReturn(Optional.of(finalized));

        job.retryPendingReconciliations();

        then(reconciliationCommandHandler).should().finalizeReconciliation(PAYMENT_ID);
    }

    @Test
    @DisplayName("should skip records without all required legs")
    void skipsRecordsWithoutAllLegs() {
        var partialRecord = ReconciliationRecord.create(PAYMENT_ID, new BigDecimal("0.01"));
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PENDING)).willReturn(List.of());
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PARTIAL)).willReturn(List.of(partialRecord));

        job.retryPendingReconciliations();

        then(reconciliationCommandHandler).should(never()).finalizeReconciliation(PAYMENT_ID);
    }

    @Test
    @DisplayName("should handle no candidates gracefully")
    void handlesNoCandidates() {
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PENDING)).willReturn(List.of());
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PARTIAL)).willReturn(List.of());

        job.retryPendingReconciliations();

        then(reconciliationCommandHandler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should process both PENDING and PARTIAL records")
    void processesBothStatuses() {
        var pendingPaymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var pendingRecord = aRecordWithAllRequiredLegsForPayment(pendingPaymentId);
        var partialRecord = aRecordWithAllRequiredLegs();

        given(reconciliationRepository.findByStatus(ReconciliationStatus.PENDING)).willReturn(List.of(pendingRecord));
        given(reconciliationRepository.findByStatus(ReconciliationStatus.PARTIAL)).willReturn(List.of(partialRecord));
        given(reconciliationCommandHandler.finalizeReconciliation(pendingPaymentId)).willReturn(Optional.empty());
        given(reconciliationCommandHandler.finalizeReconciliation(PAYMENT_ID)).willReturn(Optional.empty());

        job.retryPendingReconciliations();

        then(reconciliationCommandHandler).should().finalizeReconciliation(pendingPaymentId);
        then(reconciliationCommandHandler).should().finalizeReconciliation(PAYMENT_ID);
    }

    private static ReconciliationRecord aRecordWithAllRequiredLegsForPayment(UUID paymentId) {
        var record = ReconciliationRecord.create(paymentId, new BigDecimal("0.01"));
        record = record.addLeg(com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatInLeg());
        record = record.addLeg(com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinMintedLeg());
        record = record.addLeg(com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aChainTransferredLeg());
        record = record.addLeg(com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinRedeemedLeg());
        record = record.addLeg(com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatOutLeg());
        return record;
    }
}
