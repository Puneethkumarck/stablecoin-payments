package com.stablecoin.payments.fx.domain.port;

import com.stablecoin.payments.fx.domain.model.RateSnapshot;

public interface RateHistoryRepository {
    void record(RateSnapshot snapshot);
}
