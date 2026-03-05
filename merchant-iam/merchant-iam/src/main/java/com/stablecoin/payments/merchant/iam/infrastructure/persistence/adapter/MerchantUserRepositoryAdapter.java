package com.stablecoin.payments.merchant.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.merchant.iam.domain.team.MerchantUserRepository;
import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.mapper.MerchantUserEntityMapper;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.MerchantUserJpaRepository;
import com.stablecoin.payments.merchant.iam.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MerchantUserRepositoryAdapter implements MerchantUserRepository {

    private final MerchantUserJpaRepository jpa;
    private final RoleJpaRepository roleJpa;
    private final MerchantUserEntityMapper mapper;

    @Override
    public Optional<MerchantUser> findById(UUID userId) {
        return jpa.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<MerchantUser> findByMerchantIdAndEmailHash(UUID merchantId, String emailHash) {
        return jpa.findByMerchantIdAndEmailHash(merchantId, emailHash).map(mapper::toDomain);
    }

    @Override
    public List<MerchantUser> findByMerchantId(UUID merchantId) {
        return jpa.findByMerchantIdAndStatusNot(merchantId, "DEACTIVATED").stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByMerchantIdAndEmailHash(UUID merchantId, String emailHash) {
        return jpa.existsByMerchantIdAndEmailHash(merchantId, emailHash);
    }

    @Override
    public long countActiveAdmins(UUID merchantId, UUID adminRoleId) {
        return jpa.countByMerchantIdAndRole_RoleNameAndStatusNot(merchantId, "ADMIN", "DEACTIVATED");
    }

    @Override
    public MerchantUser save(MerchantUser user) {
        var existing = jpa.findById(user.userId());
        if (existing.isPresent()) {
            var entity = existing.get();
            entity.setEmailHash(user.emailHash());
            entity.setFullName(user.fullName());
            entity.setStatus(user.status().name());
            entity.setMfaEnabled(user.mfaEnabled());
            entity.setMfaSecretRef(user.mfaSecretRef());
            entity.setLastLoginAt(user.lastLoginAt());
            entity.setPasswordHash(user.passwordHash());
            entity.setAuthProvider(user.authProvider().name());
            entity.setUpdatedAt(user.updatedAt());
            entity.setActivatedAt(user.activatedAt());
            entity.setSuspendedAt(user.suspendedAt());
            entity.setDeactivatedAt(user.deactivatedAt());
            // Use getReferenceById to obtain a Hibernate proxy for the FK — never mutate
            // the roleId of a managed RoleEntity (causes "Identifier altered" on flush).
            if (!entity.getRole().getRoleId().equals(user.roleId())) {
                entity.setRole(roleJpa.getReferenceById(user.roleId()));
            }
            return mapper.toDomain(jpa.save(entity));
        }
        var entity = mapper.toEntity(user);
        entity.setRole(roleJpa.getReferenceById(user.roleId()));
        return mapper.toDomain(jpa.save(entity));
    }
}
