package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import com.stablecoin.payments.orchestrator.domain.event.PaymentCompensationStarted;
import com.stablecoin.payments.orchestrator.domain.event.PaymentFailed;

import java.util.UUID;

/**
 * Serializable DTO for publishing payment events from the Temporal workflow.
 * <p>
 * Uses Strings for enum fields to ensure clean Temporal serialization.
 * The activity implementation maps this back to the appropriate domain event.
 *
 * @param eventType   event type identifier matching the domain event TOPIC constant
 * @param paymentId   payment aggregate ID (used as Kafka partition key)
 * @param correlationId trace correlation ID for distributed tracing
 * @param failedState state at which failure occurred (only for payment.failed)
 * @param reason      failure or cancellation reason
 * @param errorCode   error code (only for payment.failed)
 */
public record PaymentEventRequest(
        String eventType,
        UUID paymentId,
        UUID correlationId,
        String failedState,
        String reason,
        String errorCode
) {

    public static PaymentEventRequest failed(UUID paymentId, UUID correlationId,
                                             String failedState, String reason,
                                             String errorCode) {
        return new PaymentEventRequest(PaymentFailed.TOPIC, paymentId, correlationId,
                failedState, reason, errorCode);
    }

    public static PaymentEventRequest cancelled(UUID paymentId, UUID correlationId,
                                                String reason) {
        return new PaymentEventRequest(PaymentCompensationStarted.TOPIC, paymentId, correlationId,
                null, reason, null);
    }
}
