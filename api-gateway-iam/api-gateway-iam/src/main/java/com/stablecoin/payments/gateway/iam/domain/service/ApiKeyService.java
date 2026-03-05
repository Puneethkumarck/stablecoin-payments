package com.stablecoin.payments.gateway.iam.domain.service;

import com.stablecoin.payments.gateway.iam.domain.event.ApiKeyRevokedEvent;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyExpiredException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyRevokedException;
import com.stablecoin.payments.gateway.iam.domain.exception.IpNotAllowedException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyGenerator;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyHasher;
import com.stablecoin.payments.gateway.iam.domain.port.ApiKeyRepository;
import com.stablecoin.payments.gateway.iam.domain.port.EventPublisher;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;
    private final EventPublisher<ApiKeyRevokedEvent> eventPublisher;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         MerchantRepository merchantRepository,
                         ApiKeyGenerator apiKeyGenerator,
                         ApiKeyHasher apiKeyHasher,
                         EventPublisher<ApiKeyRevokedEvent> eventPublisher) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.eventPublisher = eventPublisher;
    }

    public CreateApiKeyResult create(UUID merchantId, String name, ApiKeyEnvironment environment,
                                     List<String> scopes, List<String> allowedIps, Instant expiresAt) {
        var merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> MerchantNotFoundException.byId(merchantId));

        if (!merchant.isActive()) {
            throw MerchantNotActiveException.of(merchantId);
        }

        if (scopes != null && !merchant.getScopes().containsAll(scopes)) {
            throw ScopeExceededException.of(scopes, merchant.getScopes());
        }

        var generated = apiKeyGenerator.generate(environment);
        var hash = apiKeyHasher.hash(generated.rawKey());

        var apiKey = ApiKey.builder()
                .keyId(UUID.randomUUID())
                .merchantId(merchantId)
                .keyHash(hash)
                .keyPrefix(generated.prefix())
                .name(name)
                .environment(environment)
                .scopes(scopes != null ? List.copyOf(scopes) : List.copyOf(merchant.getScopes()))
                .allowedIps(allowedIps != null ? List.copyOf(allowedIps) : List.of())
                .active(true)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        var saved = apiKeyRepository.save(apiKey);
        return new CreateApiKeyResult(saved, generated.rawKey());
    }

    public void revoke(UUID keyId) {
        var apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> ApiKeyNotFoundException.byId(keyId));

        apiKey.revoke();
        apiKeyRepository.save(apiKey);

        eventPublisher.publish(new ApiKeyRevokedEvent(
                apiKey.getKeyId(),
                apiKey.getMerchantId(),
                apiKey.getKeyPrefix(),
                apiKey.getRevokedAt()
        ));
    }

    public ApiKey validate(String rawKey, String sourceIp) {
        var hash = apiKeyHasher.hash(rawKey);
        var apiKey = apiKeyRepository.findByKeyHash(hash)
                .orElseThrow(ApiKeyNotFoundException::byHash);

        if (!apiKey.isActive()) {
            throw ApiKeyRevokedException.of(apiKey.getKeyId());
        }

        if (apiKey.isExpired()) {
            throw ApiKeyExpiredException.of(apiKey.getKeyId());
        }

        if (!apiKey.isIpAllowed(sourceIp)) {
            throw IpNotAllowedException.of(sourceIp);
        }

        return apiKey;
    }

    public record CreateApiKeyResult(ApiKey apiKey, String rawKey) {}
}
