package com.stablecoin.payments.fx.infrastructure.scheduling;

import com.stablecoin.payments.fx.domain.model.Corridor;
import com.stablecoin.payments.fx.domain.model.RateSnapshot;
import com.stablecoin.payments.fx.domain.model.RateSourceType;
import com.stablecoin.payments.fx.domain.port.RateCache;
import com.stablecoin.payments.fx.domain.port.RateHistoryRepository;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.fx.rate-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class RateRefreshJob {

    static final List<Corridor> SUPPORTED_CORRIDORS = List.of(
            new Corridor("USD", "EUR"),
            new Corridor("EUR", "USD")
    );

    private final RateProvider rateProvider;
    private final RateCache rateCache;
    private final RateHistoryRepository rateHistoryRepository;

    @Scheduled(fixedDelayString = "${app.fx.rate-refresh.interval-ms:5000}")
    public void refreshRates() {
        log.debug("Starting rate refresh for {} corridors", SUPPORTED_CORRIDORS.size());
        int refreshed = 0;

        for (var corridor : SUPPORTED_CORRIDORS) {
            try {
                var rateOpt = rateProvider.getRate(corridor.fromCurrency(), corridor.toCurrency());
                if (rateOpt.isPresent()) {
                    var rate = rateOpt.get();
                    rateCache.put(corridor.fromCurrency(), corridor.toCurrency(), rate);

                    var snapshot = RateSnapshot.fromCorridorRate(rate, RateSourceType.CEX);
                    rateHistoryRepository.record(snapshot);
                    refreshed++;
                } else {
                    log.warn("No rate available for corridor {}", corridor.key());
                }
            } catch (Exception e) {
                log.error("Failed to refresh rate for corridor {}: {}", corridor.key(), e.getMessage());
            }
        }

        log.debug("Rate refresh complete: {}/{} corridors updated", refreshed, SUPPORTED_CORRIDORS.size());
    }
}
