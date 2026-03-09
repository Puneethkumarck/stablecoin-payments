package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.CollectionFailedEvent;
import com.stablecoin.payments.onramp.domain.event.CollectionInitiatedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;
import com.stablecoin.payments.onramp.domain.model.PspIdentifier;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentRequest;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain command handler for collection order operations.
 * <p>
 * Orchestrates: idempotency check -> create order -> call PSP ->
 * transition state -> record PspTransaction -> publish event -> save.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CollectionCommandHandler {

    private final CollectionOrderRepository collectionOrderRepository;
    private final PspTransactionRepository pspTransactionRepository;
    private final PspGateway pspGateway;
    private final CollectionEventPublisher eventPublisher;

    /**
     * Initiates a new collection order for a payment.
     * <p>
     * Idempotent: if a collection order already exists for the given paymentId,
     * returns the existing order with {@code created = false}.
     *
     * @param paymentId     the payment identifier
     * @param correlationId the correlation identifier for tracing
     * @param amount        the amount to collect
     * @param paymentRail   the payment rail details
     * @param psp           the PSP identifier
     * @param senderAccount the sender bank account details
     * @return a {@link CollectionResult} indicating the order and whether it was newly created
     */
    public CollectionResult initiateCollection(UUID paymentId, UUID correlationId,
                                               Money amount, PaymentRail paymentRail,
                                               PspIdentifier psp, BankAccount senderAccount) {
        // 1. Idempotency: check if collection order already exists for this paymentId
        var existing = collectionOrderRepository.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            log.info("Collection order already exists for paymentId={} collectionId={} status={}",
                    paymentId, existing.get().collectionId(), existing.get().status());
            return new CollectionResult(existing.get(), false);
        }

        // 2. Create new collection order in PENDING state
        var order = CollectionOrder.initiate(paymentId, correlationId, amount, paymentRail, psp, senderAccount);

        // 3. Call PSP to initiate payment (collectionId as idempotency key for safe retries)
        var pspResult = pspGateway.initiatePayment(new PspPaymentRequest(
                order.collectionId(), amount, paymentRail, senderAccount, psp.pspName(),
                order.collectionId().toString()));

        // 4. Transition: PENDING -> PAYMENT_INITIATED -> AWAITING_CONFIRMATION
        order = order.initiatePayment();
        order = order.awaitConfirmation(pspResult.pspReference());

        // 5. Save collection order first (PspTransaction FK depends on it)
        order = collectionOrderRepository.save(order);

        // 6. Record PspTransaction
        var pspTransaction = PspTransaction.create(
                order.collectionId(),
                psp.pspName(),
                pspResult.pspReference(),
                PspTransactionDirection.DEBIT,
                "payment_intent.created",
                amount,
                pspResult.status(),
                null);
        pspTransactionRepository.save(pspTransaction);

        // 7. Publish CollectionInitiatedEvent via outbox
        eventPublisher.publish(new CollectionInitiatedEvent(
                order.collectionId(),
                paymentId,
                correlationId,
                amount.amount(),
                amount.currency(),
                paymentRail.rail().name(),
                psp.pspName(),
                Instant.now()));

        log.info("Collection initiated collectionId={} paymentId={} pspRef={}",
                order.collectionId(), paymentId, pspResult.pspReference());

        return new CollectionResult(order, true);
    }

    /**
     * Retrieves a collection order by its ID.
     *
     * @param collectionId the collection order identifier
     * @return the collection order
     * @throws CollectionOrderNotFoundException if the order is not found
     */
    public CollectionOrder getCollection(UUID collectionId) {
        return collectionOrderRepository.findById(collectionId)
                .orElseThrow(() -> new CollectionOrderNotFoundException(collectionId));
    }

    /**
     * Retrieves a collection order by its associated payment ID.
     *
     * @param paymentId the payment identifier
     * @return the collection order
     * @throws CollectionOrderNotFoundException if the order is not found
     */
    public CollectionOrder getCollectionByPaymentId(UUID paymentId) {
        return collectionOrderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new CollectionOrderNotFoundException(paymentId));
    }

    /**
     * Expires a collection order that has been in AWAITING_CONFIRMATION past its timeout.
     * <p>
     * Transitions the order to COLLECTION_FAILED, persists, and publishes a
     * {@link CollectionFailedEvent} via the outbox.
     *
     * @param order the expired order
     * @param now   the current timestamp
     */
    public void expireCollection(CollectionOrder order, Instant now) {
        var expired = order.timeoutCollection("Collection expired", "OR-3001");
        collectionOrderRepository.save(expired);

        eventPublisher.publish(new CollectionFailedEvent(
                expired.collectionId(),
                expired.paymentId(),
                expired.correlationId(),
                "Collection expired",
                "OR-3001",
                now));

        log.info("Expired collection order collectionId={} paymentId={}",
                expired.collectionId(), expired.paymentId());
    }
}
