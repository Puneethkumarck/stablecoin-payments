package com.stablecoin.payments.custody.infrastructure.messaging;

import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox-based event publisher for the blockchain custody service.
 * Schedules domain events into the Namastack outbox within
 * the caller's existing transaction.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fallback-adapters.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class CustodyOutboxEventPublisher implements TransferEventPublisher {

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
                var method = event.getClass().getMethod("transferId");
                return String.valueOf(method.invoke(event));
            } catch (Exception e2) {
                throw new IllegalArgumentException(
                        "Event class missing accessor for 'paymentId' or 'transferId': "
                                + event.getClass().getName(), e2);
            }
        }
    }
}
