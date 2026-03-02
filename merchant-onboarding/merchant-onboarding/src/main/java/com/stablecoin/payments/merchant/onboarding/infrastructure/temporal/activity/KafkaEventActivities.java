package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Activity for publishing domain events to Kafka. Replaces the outbox pattern for workflow-managed events — Temporal
 * guarantees at-least-once execution.
 */
@ActivityInterface
public interface KafkaEventActivities {

  void publishEvent(String topic, String key, String payload);
}
