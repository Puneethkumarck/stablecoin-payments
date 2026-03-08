package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import com.stablecoin.payments.fx.application.service.LiquidityPoolApplicationService;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidityPoolController")
class LiquidityPoolControllerTest {

    @Mock
    private LiquidityPoolApplicationService liquidityPoolApplicationService;

    @InjectMocks
    private LiquidityPoolController controller;

    @Nested
    @DisplayName("GET /v1/liquidity/pools")
    class ListPools {

        @Test
        @DisplayName("should delegate to application service and return pool list")
        void shouldListPools() {
            // given
            var now = Instant.now();
            var pools = List.of(
                    new LiquidityPoolResponse(
                            UUID.randomUUID(), "USD", "EUR",
                            new BigDecimal("1000000.00"), BigDecimal.ZERO,
                            new BigDecimal("100000.00"), new BigDecimal("5000000.00"),
                            BigDecimal.ZERO, "HEALTHY", now),
                    new LiquidityPoolResponse(
                            UUID.randomUUID(), "GBP", "EUR",
                            new BigDecimal("500000.00"), BigDecimal.ZERO,
                            new BigDecimal("50000.00"), new BigDecimal("2000000.00"),
                            BigDecimal.ZERO, "HEALTHY", now)
            );
            given(liquidityPoolApplicationService.listPools()).willReturn(pools);

            // when
            var result = controller.listPools();

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(pools);
        }

        @Test
        @DisplayName("should return empty list when no pools exist")
        void shouldReturnEmptyList() {
            // given
            given(liquidityPoolApplicationService.listPools()).willReturn(List.of());

            // when
            var result = controller.listPools();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /v1/liquidity/pools/{poolId}")
    class GetPool {

        @Test
        @DisplayName("should delegate to application service and return pool response")
        void shouldGetPool() {
            // given
            var poolId = UUID.randomUUID();
            var now = Instant.now();
            var expectedResponse = new LiquidityPoolResponse(
                    poolId, "USD", "EUR",
                    new BigDecimal("1000000.00"), BigDecimal.ZERO,
                    new BigDecimal("100000.00"), new BigDecimal("5000000.00"),
                    BigDecimal.ZERO, "HEALTHY", now);

            given(liquidityPoolApplicationService.getPool(poolId)).willReturn(expectedResponse);

            // when
            var result = controller.getPool(poolId);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should propagate PoolNotFoundException")
        void shouldPropagatePoolNotFound() {
            // given
            var poolId = UUID.randomUUID();
            given(liquidityPoolApplicationService.getPool(poolId))
                    .willThrow(PoolNotFoundException.withId(poolId));

            // when/then
            assertThatThrownBy(() -> controller.getPool(poolId))
                    .isInstanceOf(PoolNotFoundException.class)
                    .hasMessageContaining(poolId.toString());
        }
    }
}
