package com.stablecoin.payments.fx.infrastructure.messaging;

import com.stablecoin.payments.fx.domain.port.EventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher<Object> {

    private static final List<String> KEY_FIELDS = List.of("paymentId", "poolId");

    private final Outbox outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        var key = resolveKey(event);
        outbox.schedule(event, key);
        log.debug("Scheduled outbox event type={} key={}", event.getClass().getSimpleName(), key);
    }

    private String resolveKey(Object event) {
        for (String field : KEY_FIELDS) {
            try {
                var method = event.getClass().getMethod(field);
                return String.valueOf(method.invoke(event));
            } catch (NoSuchMethodException e) {
                // try next field
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to invoke key accessor '" + field + "' on " + event.getClass().getName(), e);
            }
        }
        throw new IllegalArgumentException(
                "Event class has no key field (paymentId or poolId): " + event.getClass().getName());
    }
}
