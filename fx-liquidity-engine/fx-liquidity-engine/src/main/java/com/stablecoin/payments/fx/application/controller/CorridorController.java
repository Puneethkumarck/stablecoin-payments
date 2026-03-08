package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.application.service.LiquidityPoolApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/fx")
@RequiredArgsConstructor
public class CorridorController {

    private final LiquidityPoolApplicationService liquidityPoolApplicationService;

    @GetMapping("/corridors")
    public List<CorridorResponse> listCorridors() {
        log.info("GET /v1/fx/corridors");
        return liquidityPoolApplicationService.listCorridors();
    }
}
