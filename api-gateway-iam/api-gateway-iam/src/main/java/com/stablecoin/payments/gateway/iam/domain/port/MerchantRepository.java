package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.Merchant;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository {

    Merchant save(Merchant merchant);

    Optional<Merchant> findById(UUID merchantId);

    Optional<Merchant> findByExternalId(UUID externalId);

    boolean existsByExternalId(UUID externalId);
}
