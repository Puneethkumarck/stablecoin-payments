package com.stablecoin.payments.onramp.application.scheduler;

import com.stablecoin.payments.onramp.domain.model.ReconciliationStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.service.ReconciliationCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.COLLECTED;

/**
 * Scheduled job that runs daily reconciliation for collected orders.
 * <p>
 * Finds all COLLECTED orders that have not yet been reconciled,
 * then delegates to {@link ReconciliationCommandHandler} for each.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.onramp.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {

    private final CollectionOrderRepository collectionOrderRepository;
    private final ReconciliationCommandHandler reconciliationCommandHandler;

    @Scheduled(cron = "${app.onramp.reconciliation.cron:0 0 2 * * ?}")
    public void runReconciliation() {
        log.info("Starting reconciliation job");

        var unreconciledOrders = collectionOrderRepository.findByStatusAndNotReconciled(COLLECTED);

        if (unreconciledOrders.isEmpty()) {
            log.info("Reconciliation job completed — no unreconciled orders found");
            return;
        }

        var matchedCount = 0;
        var discrepancyCount = 0;

        for (var order : unreconciledOrders) {
            var result = reconciliationCommandHandler.reconcile(order);
            if (result.status() == ReconciliationStatus.MATCHED) {
                matchedCount++;
            } else {
                discrepancyCount++;
            }
        }

        log.info("Reconciliation job completed — total={} matched={} discrepancy={}",
                unreconciledOrders.size(), matchedCount, discrepancyCount);
    }
}
