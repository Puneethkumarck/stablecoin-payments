package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.client.FxEngineClient;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockRequest;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxReleaseRequest;
import feign.FeignException;
import io.temporal.failure.ApplicationFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.FAILED;
import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.LOCKED;

/**
 * Temporal activity implementation that calls S6 FX &amp; Liquidity Engine via REST.
 * <p>
 * Flow: GET quote → POST lock rate. Compensation releases the lock.
 * <p>
 * Idempotency key: {@code {paymentId}:fx-lock}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxLockActivityImpl implements FxLockActivity {

    private final FxEngineClient fxEngineClient;

    @Override
    public FxLockResult lockFxRate(FxLockRequest request) {
        log.info("Starting FX lock for paymentId={}, {} {} → {}",
                request.paymentId(), request.sourceAmount(),
                request.sourceCurrency(), request.targetCurrency());

        // Step 1: Get quote (4xx = non-retryable corridor/validation error)
        var quote = fxEngineClient.getQuote(
                request.sourceCurrency(),
                request.targetCurrency(),
                request.sourceAmount());

        log.info("Quote received for paymentId={}, quoteId={}, rate={}",
                request.paymentId(), quote.quoteId(), quote.rate());

        // Step 2: Lock the quoted rate (paymentId used as correlationId for idempotency)
        var lockRequest = new FxRateLockRequest(
                request.paymentId(),
                request.paymentId(),
                request.sourceCountry(),
                request.targetCountry()
        );

        FxRateLockResponse lockResponse;
        try {
            lockResponse = fxEngineClient.lockRate(quote.quoteId(), lockRequest);
        } catch (FeignException.Conflict e) {
            // 409 — idempotent success; return quote details as locked
            log.info("FX lock already exists for paymentId={} (idempotent)",
                    request.paymentId());
            return new FxLockResult(null, quote.quoteId(), quote.rate(), quote.targetAmount(),
                    quote.toCurrency(), LOCKED, null);
        } catch (FeignException.UnprocessableEntity e) {
            if (e.getMessage() != null && e.getMessage().contains("liquidity")) {
                throw ApplicationFailure.newNonRetryableFailure(
                        "Insufficient liquidity for " + request.sourceCurrency()
                                + "/" + request.targetCurrency(),
                        "INSUFFICIENT_LIQUIDITY");
            }
            return new FxLockResult(null, null, null, null, null, FAILED, e.getMessage());
        }

        log.info("FX rate locked for paymentId={}, lockId={}, quoteId={}, rate={}",
                request.paymentId(), lockResponse.lockId(),
                lockResponse.quoteId(), lockResponse.lockedRate());

        return new FxLockResult(
                lockResponse.lockId(),
                lockResponse.quoteId(),
                lockResponse.lockedRate(),
                lockResponse.targetAmount(),
                lockResponse.toCurrency(),
                LOCKED,
                null
        );
    }

    @Override
    public void releaseLock(FxReleaseRequest request) {
        log.info("Releasing FX lock lockId={} for paymentId={}, reason={}",
                request.lockId(), request.paymentId(), request.reason());
        try {
            fxEngineClient.releaseLock(request.lockId());
            log.info("FX lock released for lockId={}", request.lockId());
        } catch (FeignException.NotFound e) {
            // Idempotent — lock already released or expired
            log.info("FX lock lockId={} already released or not found", request.lockId());
        }
    }
}
