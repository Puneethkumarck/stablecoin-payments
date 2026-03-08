package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.fx.client.FxEngineClient;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockActivity;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxReleaseRequest;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestActivityEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockResult.FxLockStatus.LOCKED;
import static com.stablecoin.payments.orchestrator.fixtures.FxActivityFixtures.aFxLockRequest;
import static com.stablecoin.payments.orchestrator.fixtures.FxActivityFixtures.aLockResponse;
import static com.stablecoin.payments.orchestrator.fixtures.FxActivityFixtures.aQuoteResponse;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.LOCK_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.PAYMENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.QUOTE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("FxLockActivityImpl")
class FxLockActivityImplTest {

    private final FxEngineClient fxEngineClient = mock(FxEngineClient.class);
    private TestActivityEnvironment testActivityEnvironment;
    private FxLockActivity activity;

    @BeforeEach
    void setUp() {
        testActivityEnvironment = TestActivityEnvironment.newInstance();
        testActivityEnvironment.registerActivitiesImplementations(
                new FxLockActivityImpl(fxEngineClient));
        activity = testActivityEnvironment.newActivityStub(FxLockActivity.class);
    }

    @AfterEach
    void tearDown() {
        testActivityEnvironment.close();
    }

    @Nested
    @DisplayName("happy path — quote then lock")
    class HappyPath {

        @Test
        @DisplayName("should return LOCKED after getting quote and locking rate")
        void shouldReturnLockedAfterQuoteAndLock() {
            given(fxEngineClient.getQuote("USD", "EUR", new BigDecimal("1000.00")))
                    .willReturn(aQuoteResponse());
            given(fxEngineClient.lockRate(eq(QUOTE_ID), any()))
                    .willReturn(aLockResponse());

            var result = activity.lockFxRate(aFxLockRequest());

            var expected = new FxLockResult(
                    LOCK_ID, QUOTE_ID, new BigDecimal("0.92"),
                    new BigDecimal("920.00"), "EUR",
                    LOCKED, null);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("idempotent lock (409 conflict)")
    class IdempotentLock {

        @Test
        @DisplayName("should return LOCKED on 409 conflict (idempotent)")
        void shouldReturnLockedOnConflict() {
            given(fxEngineClient.getQuote("USD", "EUR", new BigDecimal("1000.00")))
                    .willReturn(aQuoteResponse());
            given(fxEngineClient.lockRate(eq(QUOTE_ID), any()))
                    .willThrow(new FeignException.Conflict("Conflict", dummyRequest(), null, null));

            var result = activity.lockFxRate(aFxLockRequest());

            var expected = new FxLockResult(null, QUOTE_ID, new BigDecimal("0.92"),
                    new BigDecimal("920.00"), "EUR", LOCKED, null);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("insufficient liquidity (non-retryable)")
    class InsufficientLiquidity {

        @Test
        @DisplayName("should throw non-retryable ApplicationFailure on insufficient liquidity")
        void shouldThrowNonRetryableOnInsufficientLiquidity() {
            given(fxEngineClient.getQuote("USD", "EUR", new BigDecimal("1000.00")))
                    .willReturn(aQuoteResponse());
            given(fxEngineClient.lockRate(eq(QUOTE_ID), any()))
                    .willThrow(new FeignException.UnprocessableEntity(
                            "Insufficient liquidity for corridor",
                            dummyRequest(), null, null));

            assertThatThrownBy(() -> activity.lockFxRate(aFxLockRequest()))
                    .isInstanceOf(ActivityFailure.class)
                    .hasCauseInstanceOf(ApplicationFailure.class)
                    .satisfies(e -> {
                        var af = (ApplicationFailure) e.getCause();
                        assertThat(af.getType()).isEqualTo("INSUFFICIENT_LIQUIDITY");
                        assertThat(af.isNonRetryable()).isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("release lock (compensation)")
    class ReleaseLock {

        @Test
        @DisplayName("should call S6 to release lock")
        void shouldCallS6ToReleaseLock() {
            var request = new FxReleaseRequest(LOCK_ID, PAYMENT_ID, "Cancellation");

            activity.releaseLock(request);

            then(fxEngineClient).should().releaseLock(LOCK_ID);
        }

        @Test
        @DisplayName("should handle 404 gracefully (lock already released)")
        void shouldHandleNotFoundGracefully() {
            org.mockito.BDDMockito.willThrow(new FeignException.NotFound(
                    "Not Found", dummyRequest(), null, null))
                    .given(fxEngineClient).releaseLock(LOCK_ID);

            var request = new FxReleaseRequest(LOCK_ID, PAYMENT_ID, "Cancellation");

            // Should not throw — idempotent
            activity.releaseLock(request);
        }
    }

    private Request dummyRequest() {
        return Request.create(
                Request.HttpMethod.POST, "http://localhost",
                Collections.emptyMap(), null, new RequestTemplate());
    }
}
