package com.stablecoin.payments.merchant.onboarding.infrastructure.messaging;

import com.stablecoin.payments.merchant.onboarding.domain.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMerchantEventPublisher implements EventPublisher<Object> {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        String topic = resolveField(event, "TOPIC");
        String eventType = resolveField(event, "EVENT_TYPE");
        String payload = serialize(event);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic(topic)
                .eventType(eventType)
                .payload(payload)
                .createdAt(Instant.now())
                .processed(false)
                .retryCount(0)
                .build();

        outboxEventRepository.save(outboxEvent);
        log.debug("Outbox event stored type={} topic={}", eventType, topic);
    }

    private String resolveField(Object event, String fieldName) {
        try {
            return (String) event.getClass().getField(fieldName).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Event class missing static " + fieldName + " field: " + event.getClass().getName(), e);
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize event: " + event.getClass().getName(), e);
        }
    }
}
