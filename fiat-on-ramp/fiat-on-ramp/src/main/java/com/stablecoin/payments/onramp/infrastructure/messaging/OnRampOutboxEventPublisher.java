package com.stablecoin.payments.onramp.infrastructure.messaging;

import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox-based event publisher for the on-ramp service.
 * Schedules domain events into the namastack outbox within
 * the caller's existing transaction.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class OnRampOutboxEventPublisher implements CollectionEventPublisher {

    private final Outbox outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        var key = resolveKey(event);
        outbox.schedule(event, key);
        log.debug("Scheduled outbox event type={} key={}", event.getClass().getSimpleName(), key);
    }

    private String resolveKey(Object event) {
        try {
            var method = event.getClass().getMethod("paymentId");
            return String.valueOf(method.invoke(event));
        } catch (Exception e1) {
            try {
                var method = event.getClass().getMethod("collectionId");
                return String.valueOf(method.invoke(event));
            } catch (Exception e2) {
                throw new IllegalArgumentException(
                        "Event class missing accessor for 'paymentId' or 'collectionId': "
                                + event.getClass().getName(), e2);
            }
        }
    }
}
