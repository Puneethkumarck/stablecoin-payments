package com.stablecoin.payments.custody.infrastructure.messaging;

import io.namastack.outbox.annotation.OutboxHandler;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Outbox handler that publishes events from the Namastack outbox
 * to Kafka topics. Resolves the topic from the event's static TOPIC field.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustodyOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @OutboxHandler
    public void handle(Object event, OutboxRecordMetadata metadata) {
        var topic = resolveStaticField(event, "TOPIC");
        var key = metadata.getKey();
        try {
            kafkaTemplate.send(topic, key, event).get(10, TimeUnit.SECONDS);
            log.debug("Published outbox event type={} topic={} key={}",
                    event.getClass().getSimpleName(), topic, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted publishing event type={} topic={} key={}",
                    event.getClass().getSimpleName(), topic, key);
            throw new RuntimeException("Kafka send interrupted for event " + event.getClass().getSimpleName(), e);
        } catch (Exception e) {
            log.error("Failed to publish event type={} topic={}: {}",
                    event.getClass().getSimpleName(), topic, e.getMessage());
            throw new RuntimeException("Kafka send failed for event " + event.getClass().getSimpleName(), e);
        }
    }

    private String resolveStaticField(Object event, String fieldName) {
        try {
            return (String) event.getClass().getField(fieldName).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Event class missing static " + fieldName + " field: " + event.getClass().getName(), e);
        }
    }
}
