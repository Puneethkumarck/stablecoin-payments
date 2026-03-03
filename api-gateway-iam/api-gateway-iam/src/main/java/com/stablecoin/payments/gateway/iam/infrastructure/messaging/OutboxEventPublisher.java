package com.stablecoin.payments.gateway.iam.infrastructure.messaging;

import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher<Object> {

    private final Outbox outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        var key = resolveField(event, "merchantId");
        outbox.schedule(event, key);
        log.debug("Scheduled outbox event type={} key={}", event.getClass().getSimpleName(), key);
    }

    private String resolveField(Object event, String fieldName) {
        try {
            var method = event.getClass().getMethod(fieldName);
            return String.valueOf(method.invoke(event));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Event class missing accessor for field '" + fieldName + "': "
                            + event.getClass().getName(), e);
        }
    }
}
