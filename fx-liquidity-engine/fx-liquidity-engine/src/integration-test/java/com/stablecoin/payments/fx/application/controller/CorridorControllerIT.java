package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CorridorController IT")
class CorridorControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiquidityPoolRepository poolRepository;

    @Autowired
    private RateCache rateCache;

    @Test
    @DisplayName("should return 200 OK with corridors that have cached rates")
    void shouldReturn200WithCorridorsWithRates() throws Exception {
        poolRepository.save(aUsdEurPool());
        rateCache.put("USD", "EUR", aUsdEurRate());

        mockMvc.perform(get("/v1/fx/corridors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromCurrency", is("USD")))
                .andExpect(jsonPath("$[0].toCurrency", is("EUR")))
                .andExpect(jsonPath("$[0].indicativeRate", notNullValue()))
                .andExpect(jsonPath("$[0].provider", is("REFINITIV")));
    }

    @Test
    @DisplayName("should return 200 OK with unavailable rate when no cached rate exists")
    void shouldReturn200WithUnavailableWhenNoCachedRate() throws Exception {
        poolRepository.save(aUsdEurPool());

        mockMvc.perform(get("/v1/fx/corridors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromCurrency", is("USD")))
                .andExpect(jsonPath("$[0].toCurrency", is("EUR")))
                .andExpect(jsonPath("$[0].indicativeRate", nullValue()))
                .andExpect(jsonPath("$[0].provider", is("unavailable")));
    }

    @Test
    @DisplayName("should return 200 OK with empty list when no pools exist")
    void shouldReturn200WithEmptyList() throws Exception {
        mockMvc.perform(get("/v1/fx/corridors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
