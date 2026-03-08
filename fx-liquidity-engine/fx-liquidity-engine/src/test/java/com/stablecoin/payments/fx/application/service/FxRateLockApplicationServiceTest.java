package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.event.FxRateLocked;
import com.stablecoin.payments.fx.domain.exception.InsufficientLiquidityException;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.exception.QuoteAlreadyLockedException;
import com.stablecoin.payments.fx.domain.exception.QuoteExpiredException;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.port.EventPublisher;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.aLockedQuote;
import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anExpiredQuote;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.CORRELATION_ID;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.PAYMENT_ID;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.SOURCE_COUNTRY;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.TARGET_COUNTRY;
import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.anActiveLock;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aPoolWithLowBalance;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("FxRateLockApplicationService")
class FxRateLockApplicationServiceTest {

    @Mock
    private FxQuoteRepository quoteRepository;

    @Mock
    private FxRateLockRepository lockRepository;

    @Mock
    private LiquidityPoolRepository poolRepository;

    @Mock
    private LockService lockService;

    @Mock
    private EventPublisher<Object> eventPublisher;

    @Mock
    private FxResponseMapper responseMapper;

    @InjectMocks
    private FxRateLockApplicationService service;

    @Nested
    @DisplayName("lockRate")
    class LockRate {

        @Test
        void shouldLockRateSuccessfully() {
            // given
            var quote = anActiveQuote();
            var quoteId = quote.quoteId();
            var pool = aUsdEurPool();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);

            var lockedQuote = aLockedQuote();
            var lock = anActiveLock(quoteId, PAYMENT_ID);
            var updatedPool = aUsdEurPool();
            var lockResult = new LockService.LockResult(lockedQuote, lock, updatedPool);

            var expectedResponse = new FxRateLockResponse(
                    lock.lockId(), lock.quoteId(), lock.paymentId(),
                    lock.fromCurrency(), lock.toCurrency(),
                    lock.sourceAmount(), lock.targetAmount(), lock.lockedRate(),
                    lock.feeBps(), lock.feeAmount(), lock.lockedAt(), lock.expiresAt());

            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(quote));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(pool));
            given(lockService.lockRate(quote, PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY, pool)).willReturn(lockResult);
            given(quoteRepository.save(lockedQuote)).willReturn(lockedQuote);
            given(lockRepository.save(lock)).willReturn(lock);
            given(poolRepository.save(updatedPool)).willReturn(updatedPool);
            given(responseMapper.toResponse(lock)).willReturn(expectedResponse);

            // when
            var result = service.lockRate(quoteId, request);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);

            then(eventPublisher).should().publish(any(FxRateLocked.class));
        }

        @Test
        void shouldThrowWhenQuoteNotFound() {
            // given
            var quoteId = UUID.randomUUID();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.lockRate(quoteId, request))
                    .isInstanceOf(QuoteNotFoundException.class)
                    .hasMessageContaining(quoteId.toString());

            then(lockRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        void shouldThrowWhenQuoteExpired() {
            // given
            var expiredQuote = anExpiredQuote();
            var quoteId = expiredQuote.quoteId();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(expiredQuote));

            // when/then
            assertThatThrownBy(() -> service.lockRate(quoteId, request))
                    .isInstanceOf(QuoteExpiredException.class)
                    .hasMessageContaining(quoteId.toString());

            then(lockRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        void shouldThrowWhenQuoteAlreadyLocked() {
            // given
            var lockedQuote = aLockedQuote();
            var quoteId = lockedQuote.quoteId();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(lockedQuote));

            // when/then
            assertThatThrownBy(() -> service.lockRate(quoteId, request))
                    .isInstanceOf(QuoteAlreadyLockedException.class)
                    .hasMessageContaining(quoteId.toString());

            then(lockRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        void shouldThrowWhenPoolNotFound() {
            // given
            var quote = anActiveQuote();
            var quoteId = quote.quoteId();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(quote));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.lockRate(quoteId, request))
                    .isInstanceOf(PoolNotFoundException.class)
                    .hasMessageContaining("USD:EUR");

            then(lockRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        void shouldThrowWhenInsufficientLiquidity() {
            // given
            var quote = anActiveQuote();
            var quoteId = quote.quoteId();
            var lowPool = aPoolWithLowBalance();
            var request = new FxRateLockRequest(PAYMENT_ID, CORRELATION_ID,
                    SOURCE_COUNTRY, TARGET_COUNTRY);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(quote));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(lowPool));

            // when/then
            assertThatThrownBy(() -> service.lockRate(quoteId, request))
                    .isInstanceOf(InsufficientLiquidityException.class)
                    .hasMessageContaining("USD:EUR");

            then(lockRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }
    }
}
