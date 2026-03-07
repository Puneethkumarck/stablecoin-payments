package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquidityPoolPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private LiquidityPoolRepository repository;

    @Test
    void shouldSaveAndFindPool() {
        var pool = aUsdEurPool();
        var saved = repository.save(pool);

        assertThat(repository.findById(saved.poolId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    void shouldReturnEmptyForNonExistentPool() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldFindByCorridor() {
        var pool = aUsdEurPool();
        repository.save(pool);

        assertThat(repository.findByCorridor("USD", "EUR")).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(pool);
    }

    @Test
    void shouldReturnEmptyForNonExistentCorridor() {
        assertThat(repository.findByCorridor("GBP", "JPY")).isEmpty();
    }

    @Test
    void shouldFindAll() {
        var pool1 = aUsdEurPool();
        var pool2 = new LiquidityPool(
                UUID.randomUUID(), "GBP", "USD",
                new BigDecimal("500000.00000000"), BigDecimal.ZERO,
                new BigDecimal("50000.00000000"), new BigDecimal("2000000.00000000"),
                Instant.now()
        );
        repository.save(pool1);
        repository.save(pool2);

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void shouldUpdateBalanceViaUpsert() {
        var pool = aUsdEurPool();
        repository.save(pool);

        var reserveAmount = new BigDecimal("10000.00000000");
        var updated = new LiquidityPool(
                pool.poolId(), pool.fromCurrency(), pool.toCurrency(),
                pool.availableBalance().subtract(reserveAmount),
                pool.reservedBalance().add(reserveAmount),
                pool.minimumThreshold(), pool.maximumCapacity(),
                Instant.now()
        );
        repository.save(updated);

        var expected = new LiquidityPool(
                pool.poolId(), pool.fromCurrency(), pool.toCurrency(),
                new BigDecimal("990000.00000000"), new BigDecimal("10000.00000000"),
                pool.minimumThreshold(), pool.maximumCapacity(),
                updated.updatedAt()
        );

        assertThat(repository.findById(pool.poolId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldEnforceCorridorUniqueConstraint() {
        repository.save(aUsdEurPool());

        var duplicate = new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("500000.00000000"), BigDecimal.ZERO,
                new BigDecimal("50000.00000000"), new BigDecimal("2000000.00000000"),
                Instant.now()
        );

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistDecimalPrecision() {
        var pool = new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("1234567.89012345"), new BigDecimal("98765.43210987"),
                new BigDecimal("10000.00000001"), new BigDecimal("9999999.99999999"),
                Instant.now()
        );
        repository.save(pool);

        assertThat(repository.findById(pool.poolId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(pool);
    }
}
