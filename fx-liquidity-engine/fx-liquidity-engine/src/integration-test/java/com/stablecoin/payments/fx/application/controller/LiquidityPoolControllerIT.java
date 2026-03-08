package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aGbpEurPool;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("LiquidityPoolController IT")
class LiquidityPoolControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiquidityPoolRepository poolRepository;

    @Nested
    @DisplayName("GET /v1/liquidity/pools")
    class ListPools {

        @Test
        @DisplayName("should return 200 OK with pool list")
        void shouldReturn200WithPoolList() throws Exception {
            poolRepository.save(aUsdEurPool());
            poolRepository.save(aGbpEurPool());

            mockMvc.perform(get("/v1/liquidity/pools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].poolId", notNullValue()))
                    .andExpect(jsonPath("$[0].status", notNullValue()));
        }

        @Test
        @DisplayName("should return 200 OK with empty list when no pools exist")
        void shouldReturn200WithEmptyList() throws Exception {
            mockMvc.perform(get("/v1/liquidity/pools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /v1/liquidity/pools/{poolId}")
    class GetPool {

        @Test
        @DisplayName("should return 200 OK with pool response for existing pool")
        void shouldReturn200ForExistingPool() throws Exception {
            var pool = aUsdEurPool();
            var saved = poolRepository.save(pool);

            mockMvc.perform(get("/v1/liquidity/pools/{poolId}", saved.poolId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.poolId", is(saved.poolId().toString())))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")))
                    .andExpect(jsonPath("$.status", is("HEALTHY")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing pool")
        void shouldReturn404ForNonExistingPool() throws Exception {
            var poolId = UUID.randomUUID();

            mockMvc.perform(get("/v1/liquidity/pools/{poolId}", poolId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("FX-2001")));
        }
    }
}
