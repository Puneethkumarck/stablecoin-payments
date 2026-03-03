package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.RateLimitEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitEventJpaRepository extends JpaRepository<RateLimitEventEntity, RateLimitEventEntity.RateLimitEventId> {
}
