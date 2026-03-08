package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aGbpEurPool;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidityPoolApplicationService")
class LiquidityPoolApplicationServiceTest {

    @Mock
    private LiquidityPoolRepository poolRepository;

    @Mock
    private RateCache rateCache;

    @Mock
    private FxResponseMapper responseMapper;

    @InjectMocks
    private LiquidityPoolApplicationService service;

    @Nested
    @DisplayName("listPools")
    class ListPools {

        @Test
        void shouldReturnAllPools() {
            // given
            var pool1 = aUsdEurPool();
            var pool2 = aGbpEurPool();
            var response1 = new LiquidityPoolResponse(
                    pool1.poolId(), pool1.fromCurrency(), pool1.toCurrency(),
                    pool1.availableBalance(), pool1.reservedBalance(),
                    pool1.minimumThreshold(), pool1.maximumCapacity(),
                    BigDecimal.ZERO, "HEALTHY", pool1.updatedAt());
            var response2 = new LiquidityPoolResponse(
                    pool2.poolId(), pool2.fromCurrency(), pool2.toCurrency(),
                    pool2.availableBalance(), pool2.reservedBalance(),
                    pool2.minimumThreshold(), pool2.maximumCapacity(),
                    BigDecimal.ZERO, "HEALTHY", pool2.updatedAt());

            given(poolRepository.findAll()).willReturn(List.of(pool1, pool2));
            given(responseMapper.toResponse(pool1)).willReturn(response1);
            given(responseMapper.toResponse(pool2)).willReturn(response2);

            // when
            var result = service.listPools();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0))
                    .usingRecursiveComparison()
                    .isEqualTo(response1);
            assertThat(result.get(1))
                    .usingRecursiveComparison()
                    .isEqualTo(response2);
        }

        @Test
        void shouldReturnEmptyListWhenNoPools() {
            // given
            given(poolRepository.findAll()).willReturn(List.of());

            // when
            var result = service.listPools();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPool")
    class GetPool {

        @Test
        void shouldReturnPoolById() {
            // given
            var pool = aUsdEurPool();
            var poolId = pool.poolId();
            var expectedResponse = new LiquidityPoolResponse(
                    pool.poolId(), pool.fromCurrency(), pool.toCurrency(),
                    pool.availableBalance(), pool.reservedBalance(),
                    pool.minimumThreshold(), pool.maximumCapacity(),
                    BigDecimal.ZERO, "HEALTHY", pool.updatedAt());

            given(poolRepository.findById(poolId)).willReturn(Optional.of(pool));
            given(responseMapper.toResponse(pool)).willReturn(expectedResponse);

            // when
            var result = service.getPool(poolId);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        void shouldThrowWhenPoolNotFound() {
            // given
            var poolId = UUID.randomUUID();
            given(poolRepository.findById(poolId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.getPool(poolId))
                    .isInstanceOf(PoolNotFoundException.class)
                    .hasMessageContaining(poolId.toString());
        }
    }

    @Nested
    @DisplayName("listCorridors")
    class ListCorridors {

        @Test
        void shouldReturnCorridorsWithRates() {
            // given
            var pool = aUsdEurPool();
            var corridorRate = aUsdEurRate();
            var corridorResponse = new CorridorResponse(
                    "USD", "EUR", corridorRate.rate(),
                    corridorRate.feeBps(), corridorRate.spreadBps(),
                    corridorRate.provider(), Instant.now());

            given(poolRepository.findAll()).willReturn(List.of(pool));
            given(rateCache.get("USD", "EUR")).willReturn(Optional.of(corridorRate));
            given(responseMapper.toResponse(corridorRate)).willReturn(corridorResponse);

            // when
            var result = service.listCorridors();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(corridorResponse);
        }

        @Test
        void shouldReturnCorridorWithNullRateWhenCacheMisses() {
            // given
            var pool = aUsdEurPool();
            given(poolRepository.findAll()).willReturn(List.of(pool));
            given(rateCache.get("USD", "EUR")).willReturn(Optional.empty());

            // when
            var result = service.listCorridors();

            // then
            assertThat(result).hasSize(1);
            var expected = new CorridorResponse(
                    "USD", "EUR", null, 0, 0, "unavailable", null);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
