package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.RateLimitEventEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.RateLimitEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitEventJpaRepository IT")
class RateLimitEventJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RateLimitEventJpaRepository rateLimitRepository;

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
    @DisplayName("should save and find rate limit event")
    void shouldSaveAndFind() {
        var event = GatewayEntityFixtures.aRateLimitEvent(merchantId);
        rateLimitRepository.save(event);

        var id = new RateLimitEventEntity.RateLimitEventId(event.getEventId(), event.getOccurredAt());
        var found = rateLimitRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getEndpoint()).isEqualTo("/v1/payments");
        assertThat(found.get().getTier()).isEqualTo("STARTER");
        assertThat(found.get().getRequestCount()).isEqualTo(61);
        assertThat(found.get().getLimitValue()).isEqualTo(60);
        assertThat(found.get().isBreached()).isTrue();
    }

    @Test
    @DisplayName("should save multiple events for same merchant")
    void shouldSaveMultipleEvents() {
        var event1 = GatewayEntityFixtures.aRateLimitEvent(merchantId);
        var event2 = GatewayEntityFixtures.aRateLimitEvent(merchantId);
        rateLimitRepository.save(event1);
        rateLimitRepository.save(event2);

        assertThat(rateLimitRepository.count()).isEqualTo(2);
    }
}
