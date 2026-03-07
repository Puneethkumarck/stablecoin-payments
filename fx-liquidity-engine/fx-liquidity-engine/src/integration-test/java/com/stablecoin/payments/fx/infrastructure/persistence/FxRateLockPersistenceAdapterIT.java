package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.anActiveLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxRateLockPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private FxRateLockRepository repository;

    @Autowired
    private FxQuoteRepository quoteRepository;

    @Test
    void shouldSaveAndFindRateLock() {
        var quote = saveQuote();
        var lock = anActiveLock(quote.quoteId());
        var saved = repository.save(lock);

        assertThat(repository.findById(saved.lockId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    void shouldReturnEmptyForNonExistentLock() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldFindByPaymentId() {
        var quote = saveQuote();
        var lock = anActiveLock(quote.quoteId());
        repository.save(lock);

        assertThat(repository.findByPaymentId(lock.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(lock);
    }

    @Test
    void shouldReturnEmptyForNonExistentPaymentId() {
        assertThat(repository.findByPaymentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldEnforceUniquePaymentIdConstraint() {
        var quote = saveQuote();
        var paymentId = UUID.randomUUID();

        var lock1 = new FxRateLock(
                UUID.randomUUID(), quote.quoteId(), paymentId, UUID.randomUUID(),
                "USD", "EUR", new BigDecimal("5000.00000000"), new BigDecimal("4600.00000000"),
                new BigDecimal("0.9200000000"), 30, new BigDecimal("15.00000000"),
                "US", "DE", FxRateLockStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(30), null
        );
        repository.save(lock1);

        var lock2 = new FxRateLock(
                UUID.randomUUID(), quote.quoteId(), paymentId, UUID.randomUUID(),
                "USD", "EUR", new BigDecimal("3000.00000000"), new BigDecimal("2760.00000000"),
                new BigDecimal("0.9200000000"), 30, new BigDecimal("9.00000000"),
                "US", "DE", FxRateLockStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(30), null
        );

        assertThatThrownBy(() -> repository.save(lock2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldUpdateLockStatusViaUpsert() {
        var quote = saveQuote();
        var lock = anActiveLock(quote.quoteId());
        repository.save(lock);

        var consumedAt = Instant.now();
        var consumed = new FxRateLock(
                lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                lock.fromCurrency(), lock.toCurrency(), lock.sourceAmount(), lock.targetAmount(),
                lock.lockedRate(), lock.feeBps(), lock.feeAmount(),
                lock.sourceCountry(), lock.targetCountry(),
                FxRateLockStatus.CONSUMED, lock.lockedAt(), lock.expiresAt(), consumedAt
        );
        repository.save(consumed);

        var expected = new FxRateLock(
                lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                lock.fromCurrency(), lock.toCurrency(), lock.sourceAmount(), lock.targetAmount(),
                lock.lockedRate(), lock.feeBps(), lock.feeAmount(),
                lock.sourceCountry(), lock.targetCountry(),
                FxRateLockStatus.CONSUMED, lock.lockedAt(), lock.expiresAt(), consumedAt
        );

        assertThat(repository.findById(lock.lockId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldPersistNullConsumedAt() {
        var quote = saveQuote();
        var lock = anActiveLock(quote.quoteId());
        repository.save(lock);

        assertThat(repository.findById(lock.lockId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(lock);
    }

    @Test
    void shouldPersistDecimalPrecision() {
        var quote = saveQuote();
        var lock = new FxRateLock(
                UUID.randomUUID(), quote.quoteId(), UUID.randomUUID(), UUID.randomUUID(),
                "USD", "EUR",
                new BigDecimal("12345.67890123"), new BigDecimal("11357.27639571"),
                new BigDecimal("0.9199887766"), 30, new BigDecimal("37.03703670"),
                "US", "DE", FxRateLockStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(30), null
        );
        repository.save(lock);

        assertThat(repository.findById(lock.lockId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(lock);
    }

    @Test
    void shouldRoundTripAllStatuses() {
        for (var status : FxRateLockStatus.values()) {
            var quote = saveQuote();
            var consumedAt = status == FxRateLockStatus.CONSUMED ? Instant.now() : null;
            var lock = new FxRateLock(
                    UUID.randomUUID(), quote.quoteId(), UUID.randomUUID(), UUID.randomUUID(),
                    "USD", "EUR", new BigDecimal("1000.00000000"), new BigDecimal("920.00000000"),
                    new BigDecimal("0.9200000000"), 30, new BigDecimal("3.00000000"),
                    "US", "DE", status, Instant.now(), Instant.now().plusSeconds(30), consumedAt
            );
            repository.save(lock);

            assertThat(repository.findById(lock.lockId())).isPresent().get()
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                    .isEqualTo(lock);
        }
    }

    private FxQuote saveQuote() {
        return quoteRepository.save(anActiveQuote());
    }
}
