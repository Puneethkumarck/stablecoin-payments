package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuthClientJpaRepository IT")
class OAuthClientJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private OAuthClientJpaRepository clientRepository;

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
    @DisplayName("should save and find client by id")
    void shouldSaveAndFindById() {
        var client = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        clientRepository.save(client);

        var found = clientRepository.findById(client.getClientId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test OAuth Client");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("should find active client by client id")
    void shouldFindActiveByClientId() {
        var client = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        clientRepository.save(client);

        var found = clientRepository.findByClientIdAndActiveTrue(client.getClientId());

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("should not find inactive client")
    void shouldNotFindInactiveClient() {
        var client = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        client.setActive(false);
        clientRepository.save(client);

        var found = clientRepository.findByClientIdAndActiveTrue(client.getClientId());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should deactivate all clients by merchant id and not affect other merchants")
    void shouldDeactivateAllByMerchantId() {
        var client1 = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        var client2 = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        clientRepository.save(client1);
        clientRepository.save(client2);

        // Create client for another merchant
        var otherMerchant = GatewayEntityFixtures.aPendingMerchant();
        merchantRepository.save(otherMerchant);
        var otherClient = GatewayEntityFixtures.anActiveOAuthClient(otherMerchant.getMerchantId());
        clientRepository.save(otherClient);

        int deactivated = clientRepository.deactivateAllByMerchantId(merchantId);

        assertThat(deactivated).isEqualTo(2);
        assertThat(clientRepository.findByClientIdAndActiveTrue(client1.getClientId())).isEmpty();
        assertThat(clientRepository.findByClientIdAndActiveTrue(client2.getClientId())).isEmpty();
        assertThat(clientRepository.findByClientIdAndActiveTrue(otherClient.getClientId())).isPresent();
    }

    @Test
    @DisplayName("should persist scopes and grant types as text arrays")
    void shouldPersistTextArrays() {
        var client = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        clientRepository.save(client);

        var found = clientRepository.findById(client.getClientId()).orElseThrow();

        assertThat(found.getScopes()).containsExactly("payments:read", "payments:write");
        assertThat(found.getGrantTypes()).containsExactly("client_credentials");
    }
}
