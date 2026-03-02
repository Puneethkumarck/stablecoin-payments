package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.activity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventActivitiesImpl implements KafkaEventActivities {

  private final KafkaTemplate<String, String> kafkaTemplate;

  @Override
  public void publishEvent(String topic, String key, String payload) {
    kafkaTemplate.send(topic, key, payload);
    log.info("[ACTIVITY] Published event topic={} key={}", topic, key);
  }
}
