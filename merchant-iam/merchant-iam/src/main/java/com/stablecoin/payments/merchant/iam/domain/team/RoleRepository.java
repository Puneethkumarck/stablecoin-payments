package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    Optional<Role> findById(UUID roleId);

    Optional<Role> findByMerchantIdAndRoleName(UUID merchantId, String roleName);

    List<Role> findByMerchantId(UUID merchantId);

    long countActiveUsersByRoleId(UUID roleId);

    Role save(Role role);

    List<Role> saveAll(List<Role> roles);
}
