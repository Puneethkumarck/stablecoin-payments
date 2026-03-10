package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutFailedEvent;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutMonitorProperties;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_INITIATED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_PROCESSING;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

/**
 * Detects stuck payout orders (PAYOUT_INITIATED or PAYOUT_PROCESSING beyond the
 * configured threshold) and escalates them to PAYOUT_FAILED → MANUAL_REVIEW.
 * <p>
 * Each stuck payout is processed in its own transaction to prevent one failure
 * from rolling back the entire batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutMonitorCommandHandler {

    private final PayoutOrderRepository orderRepository;
    private final PayoutEventPublisher eventPublisher;
    private final PayoutMonitorProperties monitorProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Scans for stuck payouts and escalates each to MANUAL_REVIEW.
     * Called by {@code PayoutMonitorJob} on a fixed schedule.
     */
    public void detectAndEscalateStuckPayouts() {
        var threshold = clock.instant().minus(monitorProperties.stuckThresholdMinutes(), ChronoUnit.MINUTES);

        var stuckInitiated = orderRepository.findByStatus(PAYOUT_INITIATED).stream()
                .filter(order -> order.updatedAt().isBefore(threshold))
                .toList();

        var stuckProcessing = orderRepository.findByStatus(PAYOUT_PROCESSING).stream()
                .filter(order -> order.updatedAt().isBefore(threshold))
                .toList();

        var allStuck = Stream.concat(stuckInitiated.stream(), stuckProcessing.stream()).toList();

        if (allStuck.isEmpty()) {
            log.debug("No stuck payouts found");
            return;
        }

        log.info("Found {} stuck payouts to escalate", allStuck.size());

        for (var order : allStuck) {
            processInTransaction(() -> escalateStuckPayout(order));
        }
    }

    private void escalateStuckPayout(PayoutOrder order) {
        var reason = "Payout stuck — no partner settlement received within %d minutes"
                .formatted(monitorProperties.stuckThresholdMinutes());

        var failed = order.failPayout(reason);
        var escalated = failed.escalateToManualReview();
        orderRepository.save(escalated);

        eventPublisher.publish(new FiatPayoutFailedEvent(
                escalated.payoutId(),
                escalated.paymentId(),
                escalated.correlationId(),
                escalated.failureReason(),
                escalated.errorCode(),
                clock.instant()
        ));

        log.warn("Escalated stuck payout {} (was {}) to MANUAL_REVIEW — {}",
                order.payoutId(), order.status(), reason);
    }

    private void processInTransaction(Runnable task) {
        var txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(transactionManager, txDef)
                .executeWithoutResult(status -> task.run());
    }
}
