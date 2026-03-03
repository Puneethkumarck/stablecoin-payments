package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.ApiKeyEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.ApiKeyJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyJpaRepository IT")
class ApiKeyJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyJpaRepository apiKeyRepository;

    @Autowired
    private MerchantJpaRepository merchantRepository;

    private UUID merchantId;

    @BeforeEach
    void setUpMerchant() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        merchantRepository.save(merchant);
        merchantId = merchant.getMerchantId();
    }

    @Test
    @DisplayName("should save and find api key by id")
    void shouldSaveAndFindById() {
        var apiKey = GatewayEntityFixtures.anActiveApiKey(merchantId);
        apiKeyRepository.save(apiKey);

        var found = apiKeyRepository.findById(apiKey.getKeyId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test API Key");
        assertThat(found.get().getEnvironment()).isEqualTo("LIVE");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("should find api key by hash")
    void shouldFindByKeyHash() {
        var apiKey = GatewayEntityFixtures.anActiveApiKey(merchantId);
        apiKeyRepository.save(apiKey);

        var found = apiKeyRepository.findByKeyHash(apiKey.getKeyHash());

        assertThat(found).isPresent();
        assertThat(found.get().getKeyId()).isEqualTo(apiKey.getKeyId());
    }

    @Test
    @DisplayName("should find active keys by merchant id")
    void shouldFindActiveKeysByMerchantId() {
        var key1 = GatewayEntityFixtures.anActiveApiKey(merchantId);
        var key2 = GatewayEntityFixtures.anActiveApiKey(merchantId);
        apiKeyRepository.save(key1);
        apiKeyRepository.save(key2);

        var activeKeys = apiKeyRepository.findByMerchantIdAndActiveTrue(merchantId);

        assertThat(activeKeys).hasSize(2);
    }

    @Test
    @Transactional
    @DisplayName("should deactivate all keys by merchant id and not affect other merchants")
    void shouldDeactivateAllByMerchantId() {
        var key1 = GatewayEntityFixtures.anActiveApiKey(merchantId);
        var key2 = GatewayEntityFixtures.anActiveApiKey(merchantId);
        apiKeyRepository.save(key1);
        apiKeyRepository.save(key2);

        // Create key for another merchant
        var otherMerchant = GatewayEntityFixtures.aPendingMerchant();
        merchantRepository.save(otherMerchant);
        var otherKey = GatewayEntityFixtures.anActiveApiKey(otherMerchant.getMerchantId());
        apiKeyRepository.save(otherKey);

        int deactivated = apiKeyRepository.deactivateAllByMerchantId(merchantId);

        assertThat(deactivated).isEqualTo(2);
        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(merchantId)).isEmpty();
        assertThat(apiKeyRepository.findByMerchantIdAndActiveTrue(otherMerchant.getMerchantId()))
                .hasSize(1)
                .extracting(ApiKeyEntity::getKeyId)
                .containsExactly(otherKey.getKeyId());
    }

    @Test
    @DisplayName("should persist scopes and allowed ips as text arrays")
    void shouldPersistTextArrays() {
        var apiKey = GatewayEntityFixtures.anActiveApiKey(merchantId);
        apiKeyRepository.save(apiKey);

        var found = apiKeyRepository.findById(apiKey.getKeyId()).orElseThrow();

        assertThat(found.getScopes()).containsExactly("payments:read");
        assertThat(found.getAllowedIps()).containsExactly("192.168.1.1");
    }
}
