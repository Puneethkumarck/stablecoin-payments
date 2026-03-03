package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MerchantJpaRepository IT")
class MerchantJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MerchantJpaRepository repository;

    @Test
    @DisplayName("should save and find merchant by id")
    void shouldSaveAndFindById() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        repository.save(merchant);

        var found = repository.findById(merchant.getMerchantId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Merchant");
        assertThat(found.get().getCountry()).isEqualTo("US");
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(found.get().getKybStatus()).isEqualTo("VERIFIED");
        assertThat(found.get().getRateLimitTier()).isEqualTo("STARTER");
    }

    @Test
    @DisplayName("should find merchant by external id")
    void shouldFindByExternalId() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        repository.save(merchant);

        var found = repository.findByExternalId(merchant.getExternalId());

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(merchant.getMerchantId());
    }

    @Test
    @DisplayName("should return empty when external id not found")
    void shouldReturnEmptyWhenExternalIdNotFound() {
        var found = repository.findByExternalId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should check existence by external id")
    void shouldCheckExistenceByExternalId() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        repository.save(merchant);

        assertThat(repository.existsByExternalId(merchant.getExternalId())).isTrue();
        assertThat(repository.existsByExternalId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("should persist scopes as text array")
    void shouldPersistScopesAsTextArray() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        repository.save(merchant);

        var found = repository.findById(merchant.getMerchantId()).orElseThrow();

        assertThat(found.getScopes()).containsExactly("payments:read", "payments:write");
    }

    @Test
    @DisplayName("should persist corridors as jsonb")
    void shouldPersistCorridorsAsJsonb() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        repository.save(merchant);

        var found = repository.findById(merchant.getMerchantId()).orElseThrow();

        assertThat(found.getCorridors()).isEqualTo("[]");
    }
}
