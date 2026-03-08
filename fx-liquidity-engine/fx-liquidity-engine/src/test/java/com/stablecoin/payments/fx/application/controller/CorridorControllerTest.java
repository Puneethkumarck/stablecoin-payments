package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.application.service.LiquidityPoolApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CorridorController")
class CorridorControllerTest {

    @Mock
    private LiquidityPoolApplicationService liquidityPoolApplicationService;

    @InjectMocks
    private CorridorController controller;

    @Test
    @DisplayName("should delegate to application service and return corridor list")
    void shouldListCorridors() {
        // given
        var now = Instant.now();
        var corridors = List.of(
                new CorridorResponse("USD", "EUR", new BigDecimal("0.92"), 30, 30, "REFINITIV", now),
                new CorridorResponse("GBP", "EUR", new BigDecimal("1.16"), 25, 25, "REFINITIV", now)
        );
        given(liquidityPoolApplicationService.listCorridors()).willReturn(corridors);

        // when
        var result = controller.listCorridors();

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(corridors);
    }

    @Test
    @DisplayName("should return empty list when no corridors available")
    void shouldReturnEmptyList() {
        // given
        given(liquidityPoolApplicationService.listCorridors()).willReturn(List.of());

        // when
        var result = controller.listCorridors();

        // then
        assertThat(result).isEmpty();
    }
}
