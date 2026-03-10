package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutCompletedEvent;
import com.stablecoin.payments.offramp.domain.event.FiatPayoutFailedEvent;
import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.port.OffRampTransactionRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand.EVENT_PAYMENT_FAILED;
import static com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand.EVENT_PAYMENT_SETTLED;

/**
 * Domain command handler that processes partner webhook notifications.
 * <p>
 * Orchestrates: lookup order by partnerReference -> check idempotency ->
 * transition state -> record OffRampTransaction -> publish event via outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WebhookCommandHandler {

    private final PayoutOrderRepository orderRepository;
    private final OffRampTransactionRepository transactionRepository;
    private final PayoutEventPublisher eventPublisher;

    /**
     * Processes a webhook command from a partner settlement notification.
     * <p>
     * Idempotent: if the payout order is already in COMPLETED or MANUAL_REVIEW
     * state, the webhook is skipped.
     *
     * @param command the parsed webhook command
     * @return the payout order (updated or unchanged if idempotent)
     */
    public PayoutOrder handleWebhook(PartnerWebhookCommand command) {
        log.info("Processing partner webhook eventId={} type={} partnerRef={}",
                command.eventId(), command.eventType(), command.partnerReference());

        var order = orderRepository.findByPartnerReference(command.partnerReference())
                .orElseThrow(() -> new PayoutNotFoundException(command.partnerReference()));

        if (isAlreadyProcessed(order, command.eventType())) {
            log.info("Webhook already processed — skipping. payoutId={} status={} eventType={}",
                    order.payoutId(), order.status(), command.eventType());
            return order;
        }

        recordTransaction(order, command);

        return switch (command.eventType()) {
            case EVENT_PAYMENT_SETTLED -> handleSettlement(order, command);
            case EVENT_PAYMENT_FAILED -> handleFailure(order, command);
            default -> {
                log.warn("Ignoring unrecognised webhook event type={}", command.eventType());
                yield order;
            }
        };
    }

    private PayoutOrder handleSettlement(PayoutOrder order, PartnerWebhookCommand command) {
        var settledAt = command.settledAt() != null ? command.settledAt() : Instant.now();

        var orderToComplete = order.status() == PayoutStatus.PAYOUT_INITIATED
                ? order.markPayoutProcessing()
                : order;

        var updated = orderToComplete.completePayout(command.partnerReference(), settledAt);
        updated = orderRepository.save(updated);

        eventPublisher.publish(new FiatPayoutCompletedEvent(
                updated.payoutId(),
                updated.paymentId(),
                updated.correlationId(),
                updated.fiatAmount(),
                updated.targetCurrency(),
                updated.paymentRail().name(),
                updated.partnerReference(),
                updated.partnerSettledAt()));

        log.info("Payout completed payoutId={} partnerRef={} fiatAmount={} {}",
                updated.payoutId(), updated.partnerReference(),
                updated.fiatAmount(), updated.targetCurrency());
        return updated;
    }

    private PayoutOrder handleFailure(PayoutOrder order, PartnerWebhookCommand command) {
        var reason = command.failureReason() != null
                ? command.failureReason()
                : "Partner payment failed: " + command.status();

        var updated = order.failPayout(reason);
        updated = orderRepository.save(updated);

        eventPublisher.publish(new FiatPayoutFailedEvent(
                updated.payoutId(),
                updated.paymentId(),
                updated.correlationId(),
                reason,
                updated.errorCode(),
                Instant.now()));

        log.info("Payout failed payoutId={} reason={}", updated.payoutId(), reason);
        return updated;
    }

    private boolean isAlreadyProcessed(PayoutOrder order, String eventType) {
        return switch (eventType) {
            case EVENT_PAYMENT_SETTLED -> order.status() == PayoutStatus.COMPLETED;
            case EVENT_PAYMENT_FAILED ->
                    order.status() == PayoutStatus.PAYOUT_FAILED
                            || order.status() == PayoutStatus.MANUAL_REVIEW;
            default -> false;
        };
    }

    private void recordTransaction(PayoutOrder order, PartnerWebhookCommand command) {
        var transaction = OffRampTransaction.create(
                order.payoutId(),
                command.partnerName(),
                command.eventType(),
                command.amount() != null ? command.amount() : order.fiatAmount(),
                command.currency() != null ? command.currency() : order.targetCurrency(),
                command.status() != null ? command.status() : command.eventType(),
                command.rawPayload());

        transactionRepository.save(transaction);
    }
}
