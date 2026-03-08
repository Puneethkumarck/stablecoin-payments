package com.stablecoin.payments.fx.application.mapper;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.anActiveLock;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FxResponseMapper")
class FxResponseMapperTest {

    private final FxResponseMapper mapper = new FxResponseMapper();

    @Test
    void shouldMapFxQuoteToResponse() {
        // given
        var quote = anActiveQuote();

        // when
        var result = mapper.toResponse(quote);

        // then
        var expected = new FxQuoteResponse(
                quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                quote.feeBps(), quote.feeAmount(), quote.provider(),
                quote.createdAt(), quote.expiresAt());

        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldMapFxRateLockToResponse() {
        // given
        var lock = anActiveLock(java.util.UUID.randomUUID());

        // when
        var result = mapper.toResponse(lock);

        // then
        var expected = new FxRateLockResponse(
                lock.lockId(), lock.quoteId(), lock.paymentId(),
                lock.fromCurrency(), lock.toCurrency(),
                lock.sourceAmount(), lock.targetAmount(), lock.lockedRate(),
                lock.feeBps(), lock.feeAmount(), lock.lockedAt(), lock.expiresAt());

        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldMapLiquidityPoolToResponse() {
        // given
        var pool = aUsdEurPool();

        // when
        var result = mapper.toResponse(pool);

        // then
        var expected = new LiquidityPoolResponse(
                pool.poolId(), pool.fromCurrency(), pool.toCurrency(),
                pool.availableBalance(), pool.reservedBalance(),
                pool.minimumThreshold(), pool.maximumCapacity(),
                BigDecimal.ZERO, "HEALTHY", pool.updatedAt());

        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldMapCorridorRateToResponse() {
        // given
        var corridorRate = aUsdEurRate();

        // when
        var result = mapper.toResponse(corridorRate);

        // then
        var expected = new CorridorResponse(
                corridorRate.fromCurrency(), corridorRate.toCurrency(),
                corridorRate.rate(), corridorRate.feeBps(), corridorRate.spreadBps(),
                corridorRate.provider(), result.rateUpdatedAt());

        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFields("rateUpdatedAt")
                .isEqualTo(expected);
    }
}
