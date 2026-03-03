package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantUserRepository {

    Optional<MerchantUser> findById(UUID userId);

    Optional<MerchantUser> findByMerchantIdAndEmailHash(UUID merchantId, String emailHash);

    List<MerchantUser> findByMerchantId(UUID merchantId);

    boolean existsByMerchantIdAndEmailHash(UUID merchantId, String emailHash);

    long countActiveAdmins(UUID merchantId, UUID adminRoleId);

    MerchantUser save(MerchantUser user);
}
