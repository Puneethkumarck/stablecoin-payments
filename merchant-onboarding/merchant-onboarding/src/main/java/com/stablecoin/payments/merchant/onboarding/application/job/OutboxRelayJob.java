package com.stablecoin.payments.merchant.onboarding.application.job;

import com.stablecoin.payments.merchant.onboarding.infrastructure.messaging.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:100}")
    @Transactional
    public void relay() {
        var pending = outboxEventRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        for (var event : pending) {
            kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload());
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            log.debug("Relayed outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
        }
    }
}
