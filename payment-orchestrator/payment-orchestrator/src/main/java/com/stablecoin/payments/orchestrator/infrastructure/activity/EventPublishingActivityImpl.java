package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.orchestrator.domain.event.PaymentCompensationStarted;
import com.stablecoin.payments.orchestrator.domain.event.PaymentFailed;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.domain.port.PaymentEventPublisher;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.EventPublishingActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.PaymentEventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Temporal activity implementation that publishes payment events via Namastack outbox.
 * <p>
 * Each invocation runs in its own transaction so the outbox write is atomic.
 * The {@link PaymentEventPublisher} uses {@code @Transactional(propagation = MANDATORY)},
 * which joins this activity's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublishingActivityImpl implements EventPublishingActivity {

    private final PaymentEventPublisher eventPublisher;

    @Override
    @Transactional
    public void publishPaymentEvent(PaymentEventRequest request) {
        log.info("Publishing event type={} for paymentId={}", request.eventType(), request.paymentId());

        var event = mapToEvent(request);
        eventPublisher.publish(event);

        log.info("Event published type={} paymentId={}", request.eventType(), request.paymentId());
    }

    private Object mapToEvent(PaymentEventRequest request) {
        if (PaymentFailed.TOPIC.equals(request.eventType())) {
            return new PaymentFailed(
                    request.paymentId(),
                    request.correlationId(),
                    PaymentState.valueOf(request.failedState()),
                    request.reason(),
                    request.errorCode(),
                    Instant.now()
            );
        }
        if (PaymentCompensationStarted.TOPIC.equals(request.eventType())) {
            return new PaymentCompensationStarted(
                    request.paymentId(),
                    request.correlationId(),
                    request.reason(),
                    Instant.now()
            );
        }
        throw new IllegalArgumentException("Unknown event type: " + request.eventType());
    }
}
