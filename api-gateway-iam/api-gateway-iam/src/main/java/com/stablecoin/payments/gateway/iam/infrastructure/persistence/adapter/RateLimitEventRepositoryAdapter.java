package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.RateLimitEvent;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimitEventRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.RateLimitEventEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.RateLimitEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RateLimitEventRepositoryAdapter implements RateLimitEventRepository {

    private final RateLimitEventJpaRepository jpa;

    @Override
    public RateLimitEvent save(RateLimitEvent event) {
        var eventId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID();
        var occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
        var entity = RateLimitEventEntity.builder()
                .eventId(eventId)
                .merchantId(event.getMerchantId())
                .endpoint(event.getEndpoint())
                .tier(event.getTier().name())
                .requestCount(event.getRequestCount())
                .limitValue(event.getLimitValue())
                .breached(event.isBreached())
                .occurredAt(occurredAt)
                .build();
        jpa.save(entity);
        return event.toBuilder()
                .eventId(eventId)
                .occurredAt(occurredAt)
                .build();
    }
}
