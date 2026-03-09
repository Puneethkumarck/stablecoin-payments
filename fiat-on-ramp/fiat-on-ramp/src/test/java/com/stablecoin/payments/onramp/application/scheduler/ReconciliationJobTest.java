package com.stablecoin.payments.onramp.application.scheduler;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.service.ReconciliationCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.aDiscrepancyReconciliation;
import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.aMatchedReconciliation;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationJob")
class ReconciliationJobTest {

    @Mock private CollectionOrderRepository collectionOrderRepository;
    @Mock private ReconciliationCommandHandler reconciliationCommandHandler;

    private ReconciliationJob job;

    @BeforeEach
    void setUp() {
        job = new ReconciliationJob(collectionOrderRepository, reconciliationCommandHandler);
    }

    @Test
    @DisplayName("should run reconciliation for all unreconciled collected orders")
    void shouldReconcileUnreconciledOrders() {
        // given
        var order1 = aCollectedOrder();
        var order2 = aCollectedOrder();
        var matchedRecord = aMatchedReconciliation();
        var discrepancyRecord = aDiscrepancyReconciliation();

        given(collectionOrderRepository.findByStatusAndNotReconciled(CollectionStatus.COLLECTED))
                .willReturn(List.of(order1, order2));
        given(reconciliationCommandHandler.reconcile(order1)).willReturn(matchedRecord);
        given(reconciliationCommandHandler.reconcile(order2)).willReturn(discrepancyRecord);

        // when
        job.runReconciliation();

        // then
        then(reconciliationCommandHandler).should().reconcile(order1);
        then(reconciliationCommandHandler).should().reconcile(order2);
    }

    @Test
    @DisplayName("should handle empty batch without calling reconciliation handler")
    void shouldHandleEmptyBatch() {
        // given
        given(collectionOrderRepository.findByStatusAndNotReconciled(CollectionStatus.COLLECTED))
                .willReturn(List.of());

        // when
        job.runReconciliation();

        // then
        then(reconciliationCommandHandler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should query only COLLECTED orders for reconciliation")
    void shouldQueryOnlyCollectedOrders() {
        // given
        given(collectionOrderRepository.findByStatusAndNotReconciled(CollectionStatus.COLLECTED))
                .willReturn(List.of());

        // when
        job.runReconciliation();

        // then
        then(collectionOrderRepository).should().findByStatusAndNotReconciled(CollectionStatus.COLLECTED);
    }
}
