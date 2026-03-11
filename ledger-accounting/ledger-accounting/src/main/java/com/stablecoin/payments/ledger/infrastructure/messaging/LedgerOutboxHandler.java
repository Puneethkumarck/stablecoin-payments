package com.stablecoin.payments.ledger.infrastructure.messaging;

import io.namastack.outbox.annotation.OutboxHandler;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerOutboxHandler {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @OutboxHandler
    public void handle(Object event, OutboxRecordMetadata metadata) {
        var topic = resolveStaticField(event, "TOPIC");
        var key = metadata.getKey();
        try {
            kafkaTemplate.send(topic, key, event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published outbox event type={} topic={} key={}",
                    event.getClass().getSimpleName(), topic, key);
        } catch (Exception e) {
            log.error("Failed to publish event type={} topic={}: {}",
                    event.getClass().getSimpleName(), topic, e.getMessage());
            throw new RuntimeException("Kafka send failed for " + event.getClass().getSimpleName(), e);
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
