package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidityPoolApplicationService {

    private final LiquidityPoolRepository poolRepository;
    private final RateCache rateCache;
    private final FxResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public List<LiquidityPoolResponse> listPools() {
        log.info("Listing all liquidity pools");
        return poolRepository.findAll().stream()
                .map(responseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LiquidityPoolResponse getPool(UUID poolId) {
        log.info("Getting liquidity pool: {}", poolId);
        var pool = poolRepository.findById(poolId)
                .orElseThrow(() -> PoolNotFoundException.withId(poolId));
        return responseMapper.toResponse(pool);
    }

    @Transactional(readOnly = true)
    public List<CorridorResponse> listCorridors() {
        log.info("Listing supported corridors with current rates");
        return poolRepository.findAll().stream()
                .map(pool -> rateCache.get(pool.fromCurrency(), pool.toCurrency())
                        .map(responseMapper::toResponse)
                        .orElse(new CorridorResponse(
                                pool.fromCurrency(),
                                pool.toCurrency(),
                                null,
                                0,
                                0,
                                "unavailable",
                                null
                        )))
                .toList();
    }
}
