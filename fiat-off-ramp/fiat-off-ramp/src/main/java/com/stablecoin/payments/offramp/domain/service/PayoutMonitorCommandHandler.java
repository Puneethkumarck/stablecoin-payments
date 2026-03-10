package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutFailedEvent;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutMonitorProperties;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_INITIATED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_PROCESSING;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

/**
 * Detects stuck payout orders (PAYOUT_INITIATED or PAYOUT_PROCESSING beyond the
 * configured threshold) and escalates them to PAYOUT_FAILED → MANUAL_REVIEW.
 * <p>
 * Each stuck payout is processed in its own transaction to prevent one failure
 * from rolling back the entire batch. The order is re-fetched inside the
 * transaction to guard against TOCTOU races (e.g. webhook settling concurrently).
 */
@Slf4j
@Service
public class PayoutMonitorCommandHandler {

    private final PayoutOrderRepository orderRepository;
    private final PayoutEventPublisher eventPublisher;
    private final PayoutMonitorProperties monitorProperties;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public PayoutMonitorCommandHandler(
            PayoutOrderRepository orderRepository,
            PayoutEventPublisher eventPublisher,
            PayoutMonitorProperties monitorProperties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.monitorProperties = monitorProperties;
        this.clock = clock;

        var txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = new TransactionTemplate(transactionManager, txDef);
    }

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
            try {
                requiresNewTx.executeWithoutResult(
                        status -> escalateStuckPayout(order.payoutId(), order.status()));
            } catch (Exception ex) {
                log.error("Failed to escalate payout {} — will retry next cycle",
                        order.payoutId(), ex);
            }
        }
    }

    private void escalateStuckPayout(UUID payoutId, PayoutStatus expectedStatus) {
        var order = orderRepository.findById(payoutId).orElse(null);
        if (order == null) {
            log.warn("Payout {} not found — may have been deleted", payoutId);
            return;
        }

        if (order.status() != expectedStatus) {
            log.debug("Payout {} already transitioned from {} to {} — skipping",
                    payoutId, expectedStatus, order.status());
            return;
        }

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
                payoutId, expectedStatus, reason);
    }
}
