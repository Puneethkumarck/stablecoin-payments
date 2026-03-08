package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Temporal activity for publishing payment lifecycle events to Kafka via Namastack outbox.
 * <p>
 * Events are published fire-and-forget from the workflow — failures do not block
 * the saga. The outbox guarantees at-least-once delivery to Kafka.
 */
@ActivityInterface
public interface EventPublishingActivity {

    void publishPaymentEvent(PaymentEventRequest request);
}
