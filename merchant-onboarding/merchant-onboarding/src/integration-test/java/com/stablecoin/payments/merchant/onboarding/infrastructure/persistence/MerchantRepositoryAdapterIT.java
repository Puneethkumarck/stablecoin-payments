package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence;

import com.stablecoin.payments.merchant.onboarding.AbstractIntegrationTest;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.fixtures.MerchantEntityFixtures;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MerchantRepositoryAdapter IT")
class MerchantRepositoryAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private MerchantJpaRepository jpa;

    @BeforeEach
    void cleanUp() {
        jpa.deleteAll();
    }

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("should persist and retrieve merchant with all fields")
        @Transactional
        void shouldPersistAndRetrieveMerchant() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();

            // when
            var saved = jpa.save(entity);
            var found = jpa.findById(saved.getMerchantId());

            // then
            assertThat(found).isPresent();
            var merchant = found.get();
            assertThat(merchant.getLegalName()).isEqualTo("Acme Payments Ltd");
            assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPLIED);
            assertThat(merchant.getKybStatus()).isEqualTo(KybStatus.NOT_STARTED);
            assertThat(merchant.getEntityType()).isEqualTo(EntityType.PRIVATE_LIMITED);
            assertThat(merchant.getRateLimitTier()).isEqualTo(RateLimitTier.STARTER);
        }

        @Test
        @DisplayName("should return empty for non-existent merchant")
        void shouldReturnEmptyForNonExistent() {
            var found = jpa.findById(UUID.randomUUID());
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("JSONB columns")
    class JsonbColumns {

        @Test
        @DisplayName("should persist and retrieve registered address as JSONB")
        @Transactional
        void shouldPersistRegisteredAddress() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();

            // when
            var saved = jpa.save(entity);
            jpa.flush();
            var found = jpa.findById(saved.getMerchantId()).orElseThrow();

            // then
            assertThat(found.getRegisteredAddress()).isNotNull();
            assertThat(found.getRegisteredAddress().streetLine1()).isEqualTo("123 High Street");
            assertThat(found.getRegisteredAddress().city()).isEqualTo("London");
            assertThat(found.getRegisteredAddress().country()).isEqualTo("GB");
        }

        @Test
        @DisplayName("should persist and retrieve requested corridors as JSONB")
        @Transactional
        void shouldPersistRequestedCorridors() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();

            // when
            var saved = jpa.save(entity);
            jpa.flush();
            var found = jpa.findById(saved.getMerchantId()).orElseThrow();

            // then
            assertThat(found.getRequestedCorridors()).containsExactly("GB->US");
        }

        @Test
        @DisplayName("should persist and retrieve allowed scopes as JSONB")
        @Transactional
        void shouldPersistAllowedScopes() {
            // given
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();

            // when
            var saved = jpa.save(entity);
            jpa.flush();
            var found = jpa.findById(saved.getMerchantId()).orElseThrow();

            // then
            assertThat(found.getAllowedScopes()).containsExactlyInAnyOrder("payments:read", "payments:write");
        }
    }

    @Nested
    @DisplayName("PostgreSQL native enum types")
    class EnumMapping {

        @Test
        @DisplayName("should correctly persist all enum types")
        @Transactional
        void shouldPersistAllEnumTypes() {
            // given
            var entity = MerchantEntityFixtures.anActiveMerchantEntity();

            // when
            var saved = jpa.save(entity);
            jpa.flush();
            var found = jpa.findById(saved.getMerchantId()).orElseThrow();

            // then
            assertThat(found.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
            assertThat(found.getKybStatus()).isEqualTo(KybStatus.PASSED);
            assertThat(found.getEntityType()).isEqualTo(EntityType.PRIVATE_LIMITED);
            assertThat(found.getRateLimitTier()).isEqualTo(RateLimitTier.GROWTH);
        }
    }

    @Nested
    @DisplayName("unique constraints")
    class UniqueConstraints {

        @Test
        @DisplayName("should find by registration number and country")
        @Transactional
        void shouldFindByRegistrationNumberAndCountry() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();
            jpa.save(entity);

            // when
            var found = jpa.findByRegistrationNumberAndRegistrationCountry(
                    entity.getRegistrationNumber(), "GB");

            // then
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("should check existence by registration number and country")
        @Transactional
        void shouldCheckExistence() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();
            jpa.save(entity);

            // when / then
            assertThat(jpa.existsByRegistrationNumberAndRegistrationCountry(
                    entity.getRegistrationNumber(), "GB")).isTrue();
            assertThat(jpa.existsByRegistrationNumberAndRegistrationCountry(
                    "NON-EXISTENT", "GB")).isFalse();
        }
    }

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("should increment version on update")
        @Transactional
        void shouldIncrementVersion() {
            // given
            var entity = MerchantEntityFixtures.anAppliedMerchantEntity();
            var saved = jpa.saveAndFlush(entity);
            assertThat(saved.getVersion()).isEqualTo(0L);

            // when
            saved.setTradingName("Updated Name");
            var updated = jpa.saveAndFlush(saved);

            // then
            assertThat(updated.getVersion()).isEqualTo(1L);
        }
    }
}
