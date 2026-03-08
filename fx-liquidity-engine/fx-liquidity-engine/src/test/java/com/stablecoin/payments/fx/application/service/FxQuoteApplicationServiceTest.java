package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.request.FxQuoteRequest;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.exception.RateUnavailableException;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import com.stablecoin.payments.fx.domain.service.QuoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static com.stablecoin.payments.fx.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("FxQuoteApplicationService")
class FxQuoteApplicationServiceTest {

    @Mock
    private RateProvider rateProvider;

    @Mock
    private RateCache rateCache;

    @Mock
    private QuoteService quoteService;

    @Mock
    private FxQuoteRepository quoteRepository;

    @Mock
    private FxResponseMapper responseMapper;

    @InjectMocks
    private FxQuoteApplicationService service;

    @Nested
    @DisplayName("getQuote")
    class GetQuote {

        @Test
        void shouldCreateQuoteFromCachedRate() {
            // given
            var request = new FxQuoteRequest("USD", "EUR", new BigDecimal("10000.00"));
            var corridorRate = aUsdEurRate();
            var quote = anActiveQuote();
            var expectedResponse = new FxQuoteResponse(
                    quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                    quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                    quote.feeBps(), quote.feeAmount(), quote.provider(),
                    quote.createdAt(), quote.expiresAt());

            given(rateCache.get("USD", "EUR")).willReturn(Optional.of(corridorRate));
            given(quoteService.createQuote("USD", "EUR", request.amount(), corridorRate))
                    .willReturn(quote);
            given(quoteRepository.save(quote)).willReturn(quote);
            given(responseMapper.toResponse(quote)).willReturn(expectedResponse);

            // when
            var result = service.getQuote(request);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);

            then(rateProvider).should(never()).getRate(any(), any());
        }

        @Test
        void shouldCreateQuoteFromProviderWhenCacheMisses() {
            // given
            var request = new FxQuoteRequest("USD", "EUR", new BigDecimal("10000.00"));
            var corridorRate = aUsdEurRate();
            var quote = anActiveQuote();
            var expectedResponse = new FxQuoteResponse(
                    quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                    quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                    quote.feeBps(), quote.feeAmount(), quote.provider(),
                    quote.createdAt(), quote.expiresAt());

            given(rateCache.get("USD", "EUR")).willReturn(Optional.empty());
            given(rateProvider.getRate("USD", "EUR")).willReturn(Optional.of(corridorRate));
            given(quoteService.createQuote("USD", "EUR", request.amount(), corridorRate))
                    .willReturn(quote);
            given(quoteRepository.save(quote)).willReturn(quote);
            given(responseMapper.toResponse(quote)).willReturn(expectedResponse);

            // when
            var result = service.getQuote(request);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);

            then(rateCache).should().put(eq("USD"), eq("EUR"), eqIgnoringTimestamps(corridorRate));
        }

        @Test
        void shouldThrowWhenNoRateAvailable() {
            // given
            var request = new FxQuoteRequest("USD", "EUR", new BigDecimal("10000.00"));
            given(rateCache.get("USD", "EUR")).willReturn(Optional.empty());
            given(rateProvider.getRate("USD", "EUR")).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.getQuote(request))
                    .isInstanceOf(RateUnavailableException.class)
                    .hasMessageContaining("USD:EUR");
        }
    }

    @Nested
    @DisplayName("getQuoteById")
    class GetQuoteById {

        @Test
        void shouldReturnQuoteById() {
            // given
            var quoteId = UUID.randomUUID();
            var quote = anActiveQuote();
            var expectedResponse = new FxQuoteResponse(
                    quote.quoteId(), quote.fromCurrency(), quote.toCurrency(),
                    quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.inverseRate(),
                    quote.feeBps(), quote.feeAmount(), quote.provider(),
                    quote.createdAt(), quote.expiresAt());

            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(quote));
            given(responseMapper.toResponse(quote)).willReturn(expectedResponse);

            // when
            var result = service.getQuoteById(quoteId);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        void shouldThrowWhenQuoteNotFound() {
            // given
            var quoteId = UUID.randomUUID();
            given(quoteRepository.findById(quoteId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.getQuoteById(quoteId))
                    .isInstanceOf(QuoteNotFoundException.class)
                    .hasMessageContaining(quoteId.toString());
        }
    }
}
