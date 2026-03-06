package com.stablecoin.payments.merchant.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.merchant.iam.domain.team.UserSessionRepository;
import com.stablecoin.payments.merchant.iam.domain.team.model.UserSession;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.entity.MerchantUserEntity;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.mapper.UserSessionEntityMapper;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.UserSessionJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSessionRepositoryAdapter implements UserSessionRepository {

    private final UserSessionJpaRepository jpa;
    private final UserSessionEntityMapper mapper;
    private final EntityManager entityManager;

    @Override
    public Optional<UserSession> findById(UUID sessionId) {
        return jpa.findById(sessionId).map(mapper::toDomain);
    }

    @Override
    public List<UserSession> findActiveByUserId(UUID userId) {
        return jpa.findByUser_UserIdAndRevokedFalse(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public UserSession save(UserSession session) {
        var existing = jpa.findById(session.sessionId());
        if (existing.isPresent()) {
            var entity = existing.get();
            entity.setLastActiveAt(session.lastActiveAt());
            entity.setRevoked(session.revoked());
            entity.setRevokedAt(session.revokedAt());
            entity.setRevokeReason(session.revokeReason());
            entity.setExpiresAt(session.expiresAt());
            return mapper.toDomain(jpa.save(entity));
        }
        var entity = mapper.toEntity(session);
        entity.setUser(entityManager.getReference(MerchantUserEntity.class, session.userId()));
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    @Transactional
    public int revokeAllByUserId(UUID userId, String reason) {
        return jpa.revokeAllByUserId(userId, reason);
    }

    @Override
    @Transactional
    public int revokeAllByMerchantId(UUID merchantId, String reason) {
        return jpa.revokeAllByMerchantId(merchantId, reason);
    }
}
