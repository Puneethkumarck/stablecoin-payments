package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;
import com.stablecoin.payments.offramp.domain.port.StablecoinRedemptionRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.StablecoinRedemptionJpaRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.mapper.StablecoinRedemptionPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StablecoinRedemptionPersistenceAdapter implements StablecoinRedemptionRepository {

    private final StablecoinRedemptionJpaRepository jpa;
    private final StablecoinRedemptionPersistenceMapper mapper;

    @Override
    public StablecoinRedemption save(StablecoinRedemption redemption) {
        return mapper.toDomain(jpa.save(mapper.toEntity(redemption)));
    }

    @Override
    public Optional<StablecoinRedemption> findById(UUID redemptionId) {
        return jpa.findById(redemptionId).map(mapper::toDomain);
    }

    @Override
    public Optional<StablecoinRedemption> findByPayoutId(UUID payoutId) {
        return jpa.findByPayoutId(payoutId).map(mapper::toDomain);
    }
}
