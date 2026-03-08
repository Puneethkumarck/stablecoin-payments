package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.domain.model.RateSnapshot;
import com.stablecoin.payments.fx.domain.port.RateHistoryRepository;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.RateHistoryEntity;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.RateHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RateHistoryPersistenceAdapter implements RateHistoryRepository {

    private final RateHistoryJpaRepository jpa;

    @Override
    public void record(RateSnapshot snapshot) {
        var entity = RateHistoryEntity.builder()
                .fromCurrency(snapshot.fromCurrency())
                .toCurrency(snapshot.toCurrency())
                .rate(snapshot.rate())
                .bid(snapshot.bid())
                .ask(snapshot.ask())
                .provider(snapshot.provider())
                .sourceType(snapshot.sourceType())
                .recordedAt(snapshot.recordedAt())
                .build();
        jpa.save(entity);
        log.debug("Recorded rate snapshot {}:{} rate={} provider={}",
                snapshot.fromCurrency(), snapshot.toCurrency(), snapshot.rate(), snapshot.provider());
    }
}
