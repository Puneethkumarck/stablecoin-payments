package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.ApprovedCorridorRepository;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.ApprovedCorridor;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.ApprovedCorridorJpaRepository;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.mapper.ApprovedCorridorEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApprovedCorridorRepositoryAdapter implements ApprovedCorridorRepository {

    private final ApprovedCorridorJpaRepository jpa;
    private final ApprovedCorridorEntityMapper mapper;

    @Override
    public ApprovedCorridor save(ApprovedCorridor corridor) {
        var entity = mapper.toEntity(corridor);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<ApprovedCorridor> findByMerchantId(UUID merchantId) {
        return jpa.findByMerchantId(merchantId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
