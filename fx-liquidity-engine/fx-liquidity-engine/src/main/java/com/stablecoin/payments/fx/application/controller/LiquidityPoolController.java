package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import com.stablecoin.payments.fx.application.service.LiquidityPoolApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/liquidity")
@RequiredArgsConstructor
public class LiquidityPoolController {

    private final LiquidityPoolApplicationService liquidityPoolApplicationService;

    @GetMapping("/pools")
    public List<LiquidityPoolResponse> listPools() {
        log.info("GET /v1/liquidity/pools");
        return liquidityPoolApplicationService.listPools();
    }

    @GetMapping("/pools/{poolId}")
    public LiquidityPoolResponse getPool(@PathVariable UUID poolId) {
        log.info("GET /v1/liquidity/pools/{}", poolId);
        return liquidityPoolApplicationService.getPool(poolId);
    }
}
