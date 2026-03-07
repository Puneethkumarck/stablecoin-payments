package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.model.RateSourceType;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.RateHistoryEntity;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.RateHistoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.stablecoin.payments.fx.fixtures.RateHistoryFixtures.aRateEntry;
import static org.assertj.core.api.Assertions.assertThat;

class RateHistoryJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RateHistoryJpaRepository repository;

    @Test
    void shouldSaveAndRetrieveRateHistory() {
        var entity = RateHistoryEntity.builder()
                .fromCurrency("USD")
                .toCurrency("EUR")
                .rate(new BigDecimal("0.9200000000"))
                .bid(new BigDecimal("0.9195000000"))
                .ask(new BigDecimal("0.9205000000"))
                .provider("REFINITIV")
                .sourceType(RateSourceType.CEX)
                .recordedAt(Instant.now())
                .build();
        var saved = repository.save(entity);

        assertThat(repository.findById(saved.getId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(saved);
    }

    @Test
    void shouldFindByCorridorOrderedByRecordedAtDesc() {
        var now = Instant.now();
        repository.save(aRateEntry("USD", "EUR", "0.9100000000", now.minusSeconds(60)));
        repository.save(aRateEntry("USD", "EUR", "0.9150000000", now.minusSeconds(30)));
        repository.save(aRateEntry("USD", "EUR", "0.9200000000", now));
        repository.save(aRateEntry("GBP", "USD", "1.2700000000", now));

        var results = repository.findByFromCurrencyAndToCurrencyOrderByRecordedAtDesc("USD", "EUR");

        assertThat(results).hasSize(3)
                .extracting(RateHistoryEntity::getRate)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(
                        new BigDecimal("0.9200000000"),
                        new BigDecimal("0.9150000000"),
                        new BigDecimal("0.9100000000")
                );
    }

    @Test
    void shouldReturnEmptyForNonExistentCorridor() {
        assertThat(repository.findByFromCurrencyAndToCurrencyOrderByRecordedAtDesc("JPY", "CHF"))
                .isEmpty();
    }

    @Test
    void shouldPersistNullBidAsk() {
        var entity = RateHistoryEntity.builder()
                .fromCurrency("USD")
                .toCurrency("EUR")
                .rate(new BigDecimal("0.9200000000"))
                .provider("OFFICIAL")
                .sourceType(RateSourceType.OFFICIAL)
                .recordedAt(Instant.now())
                .build();
        var saved = repository.save(entity);

        assertThat(repository.findById(saved.getId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(saved);
    }

    @Test
    void shouldPersistDecimalPrecision() {
        var entity = RateHistoryEntity.builder()
                .fromCurrency("EUR")
                .toCurrency("USD")
                .rate(new BigDecimal("1.0845671234"))
                .bid(new BigDecimal("1.0845001234"))
                .ask(new BigDecimal("1.0846341234"))
                .provider("REFINITIV")
                .sourceType(RateSourceType.CEX)
                .recordedAt(Instant.now())
                .build();
        var saved = repository.save(entity);

        assertThat(repository.findById(saved.getId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                .isEqualTo(saved);
    }

    @Test
    void shouldRoundTripAllSourceTypes() {
        for (var sourceType : RateSourceType.values()) {
            var entity = RateHistoryEntity.builder()
                    .fromCurrency("USD")
                    .toCurrency("EUR")
                    .rate(new BigDecimal("0.9200000000"))
                    .provider("TEST")
                    .sourceType(sourceType)
                    .recordedAt(Instant.now())
                    .build();
            var saved = repository.save(entity);

            assertThat(repository.findById(saved.getId())).isPresent().get()
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .withComparatorForType((a, b) -> a.truncatedTo(ChronoUnit.MICROS).compareTo(b.truncatedTo(ChronoUnit.MICROS)), Instant.class)
                    .isEqualTo(saved);
        }
    }
}
