package com.stablecoin.payments.fx.infrastructure.provider.refinitiv;

import java.math.BigDecimal;

record RefinitivRateResponse(
        String currencyPair,
        BigDecimal mid,
        BigDecimal bid,
        BigDecimal ask,
        Long timestamp
) {}
