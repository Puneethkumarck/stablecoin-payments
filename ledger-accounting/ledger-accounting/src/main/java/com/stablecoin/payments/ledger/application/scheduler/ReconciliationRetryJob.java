package com.stablecoin.payments.ledger.application.scheduler;

import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import com.stablecoin.payments.ledger.domain.service.ReconciliationCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ledger.reconciliation.retry-enabled", havingValue = "true")
public class ReconciliationRetryJob {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationCommandHandler reconciliationCommandHandler;

    @Scheduled(fixedDelayString = "${app.ledger.reconciliation.retry-interval-ms:600000}")
    public void retryPendingReconciliations() {
        log.info("[RECONCILIATION-RETRY] Starting retry job");

        var pending = reconciliationRepository.findByStatus(ReconciliationStatus.PENDING);
        var partial = reconciliationRepository.findByStatus(ReconciliationStatus.PARTIAL);

        var candidates = Stream.concat(pending.stream(), partial.stream())
                .filter(r -> r.hasAllRequiredLegs())
                .toList();

        if (candidates.isEmpty()) {
            log.info("[RECONCILIATION-RETRY] No records ready for finalization");
            return;
        }

        var reconciledCount = 0;
        var discrepancyCount = 0;

        for (var record : candidates) {
            var result = reconciliationCommandHandler.finalizeReconciliation(record.paymentId());
            if (result.isPresent()) {
                if (result.get().status() == ReconciliationStatus.RECONCILED) {
                    reconciledCount++;
                } else {
                    discrepancyCount++;
                }
            }
        }

        log.info("[RECONCILIATION-RETRY] Completed — candidates={} reconciled={} discrepancy={}",
                candidates.size(), reconciledCount, discrepancyCount);
    }
}
