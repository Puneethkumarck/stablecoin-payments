package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.OAuthClient;
import com.stablecoin.payments.gateway.iam.domain.port.OAuthClientRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper.OAuthClientEntityMapper;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.OAuthClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OAuthClientRepositoryAdapter implements OAuthClientRepository {

    private final OAuthClientJpaRepository jpa;
    private final OAuthClientEntityMapper mapper;

    @Override
    public OAuthClient save(OAuthClient client) {
        var existing = jpa.findById(client.getClientId());
        if (existing.isPresent()) {
            var entity = existing.get();
            mapper.updateEntity(client, entity);
            return mapper.toDomain(jpa.save(entity));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(client)));
    }

    @Override
    public Optional<OAuthClient> findById(UUID clientId) {
        return jpa.findById(clientId).map(mapper::toDomain);
    }

    @Override
    public Optional<OAuthClient> findActiveById(UUID clientId) {
        return jpa.findByClientIdAndActiveTrue(clientId).map(mapper::toDomain);
    }

    @Override
    public List<OAuthClient> findByMerchantId(UUID merchantId) {
        return jpa.findByMerchantId(merchantId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deactivateAllByMerchantId(UUID merchantId) {
        jpa.deactivateAllByMerchantId(merchantId);
    }
}
