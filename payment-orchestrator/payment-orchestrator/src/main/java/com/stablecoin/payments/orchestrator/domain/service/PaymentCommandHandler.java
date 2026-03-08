package com.stablecoin.payments.orchestrator.domain.service;

import com.stablecoin.payments.orchestrator.domain.model.Corridor;
import com.stablecoin.payments.orchestrator.domain.model.Money;
import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.domain.model.PaymentNotCancellableException;
import com.stablecoin.payments.orchestrator.domain.model.PaymentNotFoundException;
import com.stablecoin.payments.orchestrator.domain.port.PaymentRepository;
import com.stablecoin.payments.orchestrator.domain.workflow.PaymentWorkflow;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.CancelRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.application.config.TemporalConfig.TASK_QUEUE;

/**
 * Domain command handler for payment lifecycle operations.
 * <p>
 * Orchestrates payment creation, retrieval, and cancellation.
 * Creates the Payment aggregate, persists it, and starts the Temporal workflow.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentCommandHandler {

    private final PaymentRepository paymentRepository;
    private final WorkflowClient workflowClient;

    /**
     * Initiates a new payment or returns existing one for idempotent replay.
     *
     * @param idempotencyKey unique key for idempotent replay
     * @param correlationId  trace correlation ID
     * @param senderId       sender merchant ID
     * @param recipientId    recipient merchant ID
     * @param sourceAmount   payment amount
     * @param sourceCurrency source currency code
     * @param targetCurrency target currency code
     * @param sourceCountry  source country code
     * @param targetCountry  target country code
     * @return the created or existing Payment, and whether it was a replay
     */
    public InitiateResult initiatePayment(String idempotencyKey, UUID correlationId,
                                           UUID senderId, UUID recipientId,
                                           BigDecimal sourceAmount,
                                           String sourceCurrency, String targetCurrency,
                                           String sourceCountry, String targetCountry) {
        log.info("Initiating payment idempotencyKey={}, correlationId={}", idempotencyKey, correlationId);

        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for idempotencyKey={}, paymentId={}",
                    idempotencyKey, existing.get().paymentId());
            return new InitiateResult(existing.get(), true);
        }

        var payment = Payment.initiate(
                idempotencyKey,
                correlationId,
                senderId,
                recipientId,
                new Money(sourceAmount, sourceCurrency),
                sourceCurrency,
                targetCurrency,
                new Corridor(sourceCountry, targetCountry)
        );

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent duplicate for idempotencyKey={}, returning existing", idempotencyKey);
            var concurrent = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            return new InitiateResult(concurrent, true);
        }

        startWorkflow(saved, sourceCountry, targetCountry);

        log.info("Payment initiated paymentId={}, state={}", saved.paymentId(), saved.state());
        return new InitiateResult(saved, false);
    }

    /**
     * Retrieves a payment by its ID.
     *
     * @throws PaymentNotFoundException if no payment exists with the given ID
     */
    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    /**
     * Cancels a payment by sending a cancel signal to the Temporal workflow.
     *
     * @throws PaymentNotFoundException      if no payment exists with the given ID
     * @throws PaymentNotCancellableException if the payment is in a terminal state
     */
    public Payment cancelPayment(UUID paymentId, String reason) {
        log.info("Cancelling payment paymentId={}, reason={}", paymentId, reason);

        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.isTerminal()) {
            throw new PaymentNotCancellableException(paymentId, payment.state());
        }

        var workflowStub = workflowClient.newWorkflowStub(
                PaymentWorkflow.class,
                "payment-" + paymentId
        );
        workflowStub.cancelPayment(new CancelRequest(paymentId, reason, "API"));

        log.info("Cancel signal sent for payment paymentId={}", paymentId);
        return payment;
    }

    private void startWorkflow(Payment payment, String sourceCountry, String targetCountry) {
        var workflowOptions = WorkflowOptions.newBuilder()
                .setWorkflowId("payment-" + payment.paymentId())
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                .build();

        var workflow = workflowClient.newWorkflowStub(PaymentWorkflow.class, workflowOptions);

        var request = new PaymentRequest(
                payment.paymentId(),
                payment.idempotencyKey(),
                payment.correlationId(),
                payment.senderId(),
                payment.recipientId(),
                payment.sourceAmount().amount(),
                payment.sourceCurrency(),
                payment.targetCurrency(),
                sourceCountry,
                targetCountry
        );

        WorkflowClient.start(workflow::executePayment, request);
        log.info("Temporal workflow started workflowId=payment-{}", payment.paymentId());
    }

    /**
     * Result of initiatePayment, indicating whether this was an idempotent replay.
     */
    public record InitiateResult(Payment payment, boolean replay) {}
}
