package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository {

    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findById(UUID keyId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findActiveByMerchantId(UUID merchantId);

    void deactivateAllByMerchantId(UUID merchantId);
}
