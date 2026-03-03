package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper.ApiKeyEntityMapper;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.ApiKeyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;
    private final ApiKeyEntityMapper mapper;

    @Override
    public ApiKey save(ApiKey apiKey) {
        var existing = jpa.findById(apiKey.getKeyId());
        if (existing.isPresent()) {
            var entity = existing.get();
            mapper.updateEntity(apiKey, entity);
            return mapper.toDomain(jpa.save(entity));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(apiKey)));
    }

    @Override
    public Optional<ApiKey> findById(UUID keyId) {
        return jpa.findById(keyId).map(mapper::toDomain);
    }

    @Override
    public Optional<ApiKey> findByKeyHash(String keyHash) {
        return jpa.findByKeyHash(keyHash).map(mapper::toDomain);
    }

    @Override
    public List<ApiKey> findActiveByMerchantId(UUID merchantId) {
        return jpa.findByMerchantIdAndActiveTrue(merchantId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deactivateAllByMerchantId(UUID merchantId) {
        jpa.deactivateAllByMerchantId(merchantId);
    }
}
