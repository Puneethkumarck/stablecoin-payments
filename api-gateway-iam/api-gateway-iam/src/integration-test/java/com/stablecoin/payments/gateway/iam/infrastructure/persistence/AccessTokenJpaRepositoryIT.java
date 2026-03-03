package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AccessTokenJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.OAuthClientJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessTokenJpaRepository IT")
class AccessTokenJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AccessTokenJpaRepository tokenRepository;

    @Autowired
    private MerchantJpaRepository merchantRepository;

    @Autowired
    private OAuthClientJpaRepository clientRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID merchantId;
    private UUID clientId;

    @BeforeEach
    void setUpMerchantAndClient() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        merchantRepository.save(merchant);
        merchantId = merchant.getMerchantId();

        var client = GatewayEntityFixtures.anActiveOAuthClient(merchantId);
        clientRepository.save(client);
        clientId = client.getClientId();
    }

    @Test
    @DisplayName("should save and find token by jti")
    void shouldSaveAndFindByJti() {
        var token = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        tokenRepository.save(token);

        var found = tokenRepository.findById(token.getJti());

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(merchantId);
        assertThat(found.get().getClientId()).isEqualTo(clientId);
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("should revoke all tokens by merchant id and not affect other merchants")
    void shouldRevokeAllByMerchantId() {
        var token1 = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        var token2 = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        tokenRepository.save(token1);
        tokenRepository.save(token2);

        // Create token for another merchant
        var otherMerchant = GatewayEntityFixtures.aPendingMerchant();
        merchantRepository.save(otherMerchant);
        var otherClient = GatewayEntityFixtures.anActiveOAuthClient(otherMerchant.getMerchantId());
        clientRepository.save(otherClient);
        var otherToken = GatewayEntityFixtures.anActiveAccessToken(
                otherMerchant.getMerchantId(), otherClient.getClientId());
        tokenRepository.save(otherToken);

        int revoked = tokenRepository.revokeAllByMerchantId(merchantId);
        entityManager.flush();
        entityManager.clear();

        assertThat(revoked).isEqualTo(2);
        assertThat(tokenRepository.findById(token1.getJti()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokenRepository.findById(token2.getJti()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokenRepository.findById(otherToken.getJti()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("should delete only expired revoked tokens before cutoff")
    void shouldDeleteExpiredBefore() {
        // Expired and revoked — should be deleted
        var expiredRevokedToken = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        expiredRevokedToken.setRevoked(true);
        expiredRevokedToken.setRevokedAt(Instant.now().minusSeconds(7200));
        expiredRevokedToken.setExpiresAt(Instant.now().minusSeconds(3600));
        tokenRepository.save(expiredRevokedToken);

        // Expired but NOT revoked — should be retained
        var expiredNotRevokedToken = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        expiredNotRevokedToken.setExpiresAt(Instant.now().minusSeconds(3600));
        tokenRepository.save(expiredNotRevokedToken);

        // Active token — should be retained
        var activeToken = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        activeToken.setExpiresAt(Instant.now().plusSeconds(3600));
        tokenRepository.save(activeToken);

        int deleted = tokenRepository.deleteExpiredBefore(Instant.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.findById(expiredNotRevokedToken.getJti())).isPresent();
        assertThat(tokenRepository.findById(activeToken.getJti())).isPresent();
    }

    @Test
    @DisplayName("should persist scopes as text array")
    void shouldPersistScopesAsTextArray() {
        var token = GatewayEntityFixtures.anActiveAccessToken(merchantId, clientId);
        tokenRepository.save(token);

        var found = tokenRepository.findById(token.getJti()).orElseThrow();

        assertThat(found.getScopes()).containsExactly("payments:read");
    }
}
