package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxQuoteStatus;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static org.assertj.core.api.Assertions.assertThat;

class FxQuotePersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private FxQuoteRepository repository;

    @Test
    void shouldSaveAndFindQuote() {
        var quote = anActiveQuote();
        var saved = repository.save(quote);

        var found = repository.findById(saved.quoteId());

        assertThat(found).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("spreadBps")
                .isEqualTo(saved);
    }

    @Test
    void shouldReturnEmptyForNonExistentQuote() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldUpdateQuoteStatusViaUpsert() {
        var quote = anActiveQuote();
        repository.save(quote);

        var locked = new FxQuote(
                quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                0, quote.feeBps(), quote.feeAmount(), quote.provider(), quote.providerRef(),
                FxQuoteStatus.LOCKED, quote.createdAt(), quote.expiresAt()
        );
        repository.save(locked);

        var expected = new FxQuote(
                quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                0, quote.feeBps(), quote.feeAmount(), quote.provider(), quote.providerRef(),
                FxQuoteStatus.LOCKED, quote.createdAt(), quote.expiresAt()
        );

        assertThat(repository.findById(quote.quoteId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("spreadBps")
                .isEqualTo(expected);
    }

    @Test
    void shouldPersistDecimalPrecision() {
        var quote = new FxQuote(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("10000.12345678"), new BigDecimal("10845.67123400"),
                new BigDecimal("1.0845671234"), new BigDecimal("0.9220334567"),
                0, 30, new BigDecimal("30.00037037"), "REFINITIV", null,
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
        repository.save(quote);

        assertThat(repository.findById(quote.quoteId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("spreadBps")
                .isEqualTo(quote);
    }

    @Test
    void shouldPersistNullProviderRef() {
        var quote = new FxQuote(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("1000.00000000"), new BigDecimal("920.00000000"),
                new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                0, 30, new BigDecimal("3.00000000"), "REFINITIV", null,
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
        repository.save(quote);

        assertThat(repository.findById(quote.quoteId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("spreadBps")
                .isEqualTo(quote);
    }

    @Test
    void shouldPersistProviderRef() {
        var quote = new FxQuote(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("1000.00000000"), new BigDecimal("920.00000000"),
                new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                0, 30, new BigDecimal("3.00000000"), "REFINITIV", "REF-20260307-001",
                FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
        );
        repository.save(quote);

        assertThat(repository.findById(quote.quoteId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .ignoringFields("spreadBps")
                .isEqualTo(quote);
    }

    @Test
    void shouldRoundTripAllStatuses() {
        for (var status : FxQuoteStatus.values()) {
            var quote = new FxQuote(
                    UUID.randomUUID(), "USD", "EUR",
                    new BigDecimal("1000.00000000"), new BigDecimal("920.00000000"),
                    new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                    0, 30, new BigDecimal("3.00000000"), "REFINITIV", null,
                    status, Instant.now(), Instant.now().plusSeconds(300)
            );
            repository.save(quote);

            assertThat(repository.findById(quote.quoteId())).isPresent().get()
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                    .ignoringFields("spreadBps")
                    .isEqualTo(quote);
        }
    }
}
