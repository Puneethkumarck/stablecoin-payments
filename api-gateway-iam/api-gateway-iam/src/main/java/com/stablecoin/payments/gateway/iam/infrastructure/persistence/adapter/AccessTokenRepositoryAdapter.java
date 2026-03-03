package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.AccessToken;
import com.stablecoin.payments.gateway.iam.domain.port.AccessTokenRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.mapper.AccessTokenEntityMapper;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AccessTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccessTokenRepositoryAdapter implements AccessTokenRepository {

    private final AccessTokenJpaRepository jpa;
    private final AccessTokenEntityMapper mapper;

    @Override
    public AccessToken save(AccessToken token) {
        return mapper.toDomain(jpa.save(mapper.toEntity(token)));
    }

    @Override
    public Optional<AccessToken> findByJti(UUID jti) {
        return jpa.findById(jti).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllByMerchantId(UUID merchantId) {
        jpa.revokeAllByMerchantId(merchantId);
    }

    @Override
    @Transactional
    public void deleteExpiredBefore(Instant cutoff) {
        jpa.deleteExpiredBefore(cutoff);
    }
}
