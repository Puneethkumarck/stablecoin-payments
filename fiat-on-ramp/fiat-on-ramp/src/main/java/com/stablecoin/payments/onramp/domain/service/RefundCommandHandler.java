package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.RefundCompletedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.exception.RefundAmountExceededException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotAllowedException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotFoundException;
import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.domain.model.RefundStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspRefundRequest;
import com.stablecoin.payments.onramp.domain.port.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain command handler for refund operations.
 * <p>
 * Orchestrates: validate collection state -> check idempotency ->
 * create refund -> call PSP -> transition collection order -> publish event.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RefundCommandHandler {

    private final CollectionOrderRepository collectionOrderRepository;
    private final RefundRepository refundRepository;
    private final PspGateway pspGateway;
    private final CollectionEventPublisher eventPublisher;

    /**
     * Initiates a refund for a collected order.
     * <p>
     * Idempotent: if a refund already exists for this collection in a
     * non-failed state (PENDING, PROCESSING, COMPLETED), returns the existing one.
     *
     * @param collectionId the collection order to refund
     * @param refundAmount the amount to refund
     * @param reason       the reason for the refund
     * @return the created or existing refund
     */
    public Refund initiateRefund(UUID collectionId, Money refundAmount, String reason) {
        // 1. Find collection order
        var order = collectionOrderRepository.findById(collectionId)
                .orElseThrow(() -> new CollectionOrderNotFoundException(collectionId));

        // 2. Idempotency: check if refund already exists for this collection
        var existingRefunds = refundRepository.findByCollectionId(collectionId);
        var existingActive = existingRefunds.stream()
                .filter(r -> r.status() == RefundStatus.COMPLETED
                        || r.status() == RefundStatus.PROCESSING
                        || r.status() == RefundStatus.PENDING)
                .findFirst();
        if (existingActive.isPresent()) {
            log.info("Refund already exists for collectionId={} refundId={} status={}",
                    collectionId, existingActive.get().refundId(), existingActive.get().status());
            return existingActive.get();
        }

        // 3. Validate collection is in COLLECTED state
        if (order.status() != CollectionStatus.COLLECTED) {
            throw new RefundNotAllowedException(collectionId, order.status());
        }

        // 4. Validate refund amount <= collected amount
        if (refundAmount.amount().compareTo(order.collectedAmount().amount()) > 0) {
            throw new RefundAmountExceededException(collectionId, refundAmount, order.collectedAmount());
        }

        // 5. Create Refund in PENDING and transition to PROCESSING
        var refund = Refund.initiate(collectionId, order.paymentId(), refundAmount, reason)
                .startProcessing();

        // 6. Transition collection through refund states up to REFUND_PROCESSING
        var updatedOrder = order.initiateRefund()
                .startRefundProcessing();

        // 7. Call PSP for refund (between startRefundProcessing and completeRefund
        //    so failure leaves order in REFUND_PROCESSING for retry/compensation)
        var pspResult = pspGateway.initiateRefund(new PspRefundRequest(
                collectionId, order.pspReference(), refundAmount,
                order.psp().pspName(), reason));

        // 8. Complete refund with PSP reference: PROCESSING -> COMPLETED
        refund = refund.complete(pspResult.pspRefundRef());

        // 9. Transition collection: REFUND_PROCESSING -> REFUNDED and persist once
        updatedOrder = updatedOrder.completeRefund();
        collectionOrderRepository.save(updatedOrder);

        // 10. Save refund
        refund = refundRepository.save(refund);

        // 11. Publish RefundCompletedEvent via outbox
        eventPublisher.publish(new RefundCompletedEvent(
                refund.refundId(),
                collectionId,
                order.paymentId(),
                refundAmount.amount(),
                refundAmount.currency(),
                pspResult.pspRefundRef(),
                Instant.now()));

        log.info("Refund completed collectionId={} refundId={} pspRef={}",
                collectionId, refund.refundId(), pspResult.pspRefundRef());

        return refund;
    }

    /**
     * Retrieves a refund by its ID.
     *
     * @param refundId the refund identifier
     * @return the refund
     * @throws RefundNotFoundException if the refund is not found
     */
    public Refund getRefund(UUID refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));
    }
}
