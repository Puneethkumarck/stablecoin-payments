package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper.MerchantEntityMapper;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MerchantRepositoryAdapter implements MerchantRepository {

    private final MerchantJpaRepository jpa;
    private final MerchantEntityMapper mapper;

    @Override
    public Merchant save(Merchant merchant) {
        var existing = jpa.findById(merchant.getMerchantId());
        if (existing.isPresent()) {
            var entity = existing.get();
            mapper.updateEntity(merchant, entity);
            return mapper.toDomain(jpa.save(entity));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(merchant)));
    }

    @Override
    public Optional<Merchant> findById(UUID merchantId) {
        return jpa.findById(merchantId).map(mapper::toDomain);
    }

    @Override
    public Optional<Merchant> findByExternalId(UUID externalId) {
        return jpa.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByExternalId(UUID externalId) {
        return jpa.existsByExternalId(externalId);
    }
}
