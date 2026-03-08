package com.stablecoin.payments.orchestrator.infrastructure.messaging;

import com.stablecoin.payments.orchestrator.domain.port.PaymentEventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements PaymentEventPublisher {

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
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Event class missing accessor for field 'paymentId': "
                            + event.getClass().getName(), e);
        }
    }
}
