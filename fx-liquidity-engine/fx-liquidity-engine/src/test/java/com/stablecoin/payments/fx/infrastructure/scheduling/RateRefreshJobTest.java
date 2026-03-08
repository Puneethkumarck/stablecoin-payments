package com.stablecoin.payments.fx.infrastructure.scheduling;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.model.RateSnapshot;
import com.stablecoin.payments.fx.domain.model.RateSourceType;
import com.stablecoin.payments.fx.domain.port.RateCache;
import com.stablecoin.payments.fx.domain.port.RateHistoryRepository;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RateRefreshJobTest {

    @Mock
    private RateProvider rateProvider;

    @Mock
    private RateCache rateCache;

    @Mock
    private RateHistoryRepository rateHistoryRepository;

    @InjectMocks
    private RateRefreshJob rateRefreshJob;

    @Test
    @DisplayName("should refresh rates and record to cache and history for available corridors")
    void refreshesRatesForAvailableCorridors() {
        var usdEurRate = aUsdEurRate();
        given(rateProvider.getRate("USD", "EUR")).willReturn(Optional.of(usdEurRate));

        var eurUsdRate = CorridorRate.builder()
                .fromCurrency("EUR")
                .toCurrency("USD")
                .rate(new java.math.BigDecimal("1.0869565217"))
                .spreadBps(30)
                .feeBps(30)
                .provider("REFINITIV")
                .ageMs(1200)
                .build();
        given(rateProvider.getRate("EUR", "USD")).willReturn(Optional.of(eurUsdRate));

        rateRefreshJob.refreshRates();

        then(rateCache).should().put("USD", "EUR", usdEurRate);
        then(rateCache).should().put("EUR", "USD", eurUsdRate);

        var expectedUsdEurSnapshot = RateSnapshot.fromCorridorRate(usdEurRate, RateSourceType.CEX);
        then(rateHistoryRepository).should().record(eqIgnoringTimestamps(expectedUsdEurSnapshot));

        var expectedEurUsdSnapshot = RateSnapshot.fromCorridorRate(eurUsdRate, RateSourceType.CEX);
        then(rateHistoryRepository).should().record(eqIgnoringTimestamps(expectedEurUsdSnapshot));
    }

    @Test
    @DisplayName("should skip corridors with no rate available")
    void skipsCorridorsWithNoRate() {
        given(rateProvider.getRate("USD", "EUR")).willReturn(Optional.empty());
        given(rateProvider.getRate("EUR", "USD")).willReturn(Optional.empty());

        rateRefreshJob.refreshRates();

        then(rateCache).should(never()).put("USD", "EUR", null);
        then(rateHistoryRepository).should(never()).record(eqIgnoringTimestamps(
                RateSnapshot.fromCorridorRate(aUsdEurRate(), RateSourceType.CEX)));
    }

    @Test
    @DisplayName("should continue refreshing other corridors when one fails")
    void continuesOnProviderFailure() {
        given(rateProvider.getRate("USD", "EUR")).willThrow(new RuntimeException("provider down"));

        var eurUsdRate = CorridorRate.builder()
                .fromCurrency("EUR")
                .toCurrency("USD")
                .rate(new java.math.BigDecimal("1.0869565217"))
                .spreadBps(30)
                .feeBps(30)
                .provider("REFINITIV")
                .ageMs(500)
                .build();
        given(rateProvider.getRate("EUR", "USD")).willReturn(Optional.of(eurUsdRate));

        rateRefreshJob.refreshRates();

        then(rateCache).should().put("EUR", "USD", eurUsdRate);
        var expectedSnapshot = RateSnapshot.fromCorridorRate(eurUsdRate, RateSourceType.CEX);
        then(rateHistoryRepository).should().record(eqIgnoringTimestamps(expectedSnapshot));
    }
}
