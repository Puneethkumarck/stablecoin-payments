package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.CollectionCompletedEvent;
import com.stablecoin.payments.onramp.domain.event.CollectionFailedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Domain command handler that processes PSP webhook notifications.
 * <p>
 * Orchestrates: lookup order by pspReference -> check idempotency ->
 * transition state -> record PspTransaction -> publish event via outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WebhookCommandHandler {

    private static final String EVENT_PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_PAYMENT_FAILED = "payment_intent.payment_failed";

    private final CollectionOrderRepository orderRepository;
    private final PspTransactionRepository pspTransactionRepository;
    private final CollectionEventPublisher eventPublisher;

    /**
     * Processes a webhook command from a PSP notification.
     * <p>
     * Idempotent: if the collection order is already in a terminal or
     * completed state for the given event type, the webhook is skipped.
     *
     * @param command the parsed webhook command
     * @return the updated collection order
     */
    public CollectionOrder handleWebhook(WebhookCommand command) {
        log.info("Processing webhook eventId={} type={} pspRef={}",
                command.eventId(), command.eventType(), command.pspReference());

        var order = orderRepository.findByPspReference(command.pspReference())
                .orElseThrow(() -> new CollectionOrderNotFoundException(command.pspReference()));

        if (isAlreadyProcessed(order, command.eventType())) {
            log.info("Webhook already processed — skipping. collectionId={} status={} eventType={}",
                    order.collectionId(), order.status(), command.eventType());
            return order;
        }

        recordPspTransaction(order, command);

        return switch (command.eventType()) {
            case EVENT_PAYMENT_SUCCEEDED -> handlePaymentSucceeded(order, command);
            case EVENT_PAYMENT_FAILED -> handlePaymentFailed(order, command);
            default -> {
                log.warn("Ignoring unrecognised webhook event type={}", command.eventType());
                yield order;
            }
        };
    }

    private CollectionOrder handlePaymentSucceeded(CollectionOrder order, WebhookCommand command) {
        if (command.amount() != null && !amountsMatch(order, command)) {
            log.warn("Amount mismatch detected — expected={} collected={} collectionId={}",
                    order.amount(), command.amount(), order.collectionId());

            var updated = order.detectAmountMismatch();
            updated = orderRepository.save(updated);

            eventPublisher.publish(new CollectionFailedEvent(
                    updated.collectionId(),
                    updated.paymentId(),
                    updated.correlationId(),
                    "Amount mismatch: expected %s %s, received %s %s".formatted(
                            order.amount().amount(), order.amount().currency(),
                            command.amount().amount(), command.amount().currency()),
                    "AMOUNT_MISMATCH",
                    Instant.now()));
            return updated;
        }

        var collectedAmount = command.amount() != null ? command.amount() : order.amount();
        var updated = order.confirmCollection(collectedAmount);
        updated = orderRepository.save(updated);

        eventPublisher.publish(new CollectionCompletedEvent(
                updated.collectionId(),
                updated.paymentId(),
                updated.correlationId(),
                updated.collectedAmount().amount(),
                updated.collectedAmount().currency(),
                updated.paymentRail().rail().name(),
                updated.psp().pspName(),
                updated.pspReference(),
                updated.pspSettledAt()));

        log.info("Collection confirmed collectionId={} amount={} {}",
                updated.collectionId(), updated.collectedAmount().amount(),
                updated.collectedAmount().currency());
        return updated;
    }

    private CollectionOrder handlePaymentFailed(CollectionOrder order, WebhookCommand command) {
        var failureReason = "PSP payment failed: " + command.status();
        var errorCode = "PSP_PAYMENT_FAILED";

        CollectionOrder updated;
        if (order.status() == CollectionStatus.AWAITING_CONFIRMATION) {
            updated = order.timeoutCollection(failureReason, errorCode);
        } else {
            updated = order.failCollection(failureReason, errorCode);
        }
        updated = orderRepository.save(updated);

        eventPublisher.publish(new CollectionFailedEvent(
                updated.collectionId(),
                updated.paymentId(),
                updated.correlationId(),
                failureReason,
                errorCode,
                Instant.now()));

        log.info("Collection failed collectionId={} reason={}",
                updated.collectionId(), failureReason);
        return updated;
    }

    private boolean isAlreadyProcessed(CollectionOrder order, String eventType) {
        return switch (eventType) {
            case EVENT_PAYMENT_SUCCEEDED ->
                    order.status() == CollectionStatus.COLLECTED
                            || order.status() == CollectionStatus.REFUND_INITIATED
                            || order.status() == CollectionStatus.REFUND_PROCESSING
                            || order.status() == CollectionStatus.REFUNDED;
            case EVENT_PAYMENT_FAILED ->
                    order.status() == CollectionStatus.COLLECTION_FAILED
                            || order.status() == CollectionStatus.MANUAL_REVIEW;
            default -> false;
        };
    }

    private boolean amountsMatch(CollectionOrder order, WebhookCommand command) {
        return order.amount().amount().compareTo(command.amount().amount()) == 0
                && order.amount().currency().equalsIgnoreCase(command.amount().currency());
    }

    private void recordPspTransaction(CollectionOrder order, WebhookCommand command) {
        var pspTransaction = PspTransaction.create(
                order.collectionId(),
                order.psp().pspName(),
                command.pspReference(),
                PspTransactionDirection.DEBIT,
                command.eventType(),
                command.amount() != null ? command.amount() : order.amount(),
                command.status() != null ? command.status() : command.eventType(),
                command.rawPayload());

        pspTransactionRepository.save(pspTransaction);
    }
}
