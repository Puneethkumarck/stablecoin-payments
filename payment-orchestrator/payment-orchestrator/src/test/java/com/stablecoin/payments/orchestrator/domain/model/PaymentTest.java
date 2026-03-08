package com.stablecoin.payments.orchestrator.domain.model;

import com.stablecoin.payments.orchestrator.domain.statemachine.StateMachineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPENSATING_FIAT_REFUND;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPENSATING_STABLECOIN_RETURN;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPLETED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPLIANCE_CHECK;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.FAILED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.FIAT_COLLECTED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.FIAT_COLLECTION_PENDING;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.INITIATED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.OFF_RAMP_INITIATED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.ON_CHAIN_CONFIRMED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.ON_CHAIN_SUBMITTED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.SETTLED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.COMPLETE;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.FAIL;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.START_COMPENSATION;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.START_COMPLIANCE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.BASE_CHAIN;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.CORRELATION_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_AMOUNT;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TX_HASH;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.US_TO_DE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aCompensatingFiatRefundPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aCompensatingStablecoinReturnPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aCompletedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aComplianceCheckPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aFailedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aFiatCollectedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aFiatCollectionPendingPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aSettledPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aValidFxRate;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anFxLockedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiatedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anOffRampInitiatedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anOnChainConfirmedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anOnChainSubmittedPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment aggregate root")
class PaymentTest {

    // ── Factory Method ────────────────────────────────────────────────

    @Nested
    @DisplayName("Factory Method — initiate()")
    class FactoryMethod {

        @Test
        @DisplayName("creates payment in INITIATED state with all required fields")
        void createsPaymentInInitiatedState() {
            var payment = anInitiatedPayment();

            var expected = Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE
            );

            assertThat(payment)
                    .usingRecursiveComparison()
                    .ignoringFields("paymentId", "createdAt", "updatedAt", "expiresAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates unique paymentId")
        void generatesUniquePaymentId() {
            var payment = anInitiatedPayment();

            assertThat(payment.paymentId()).isNotNull();
        }

        @Test
        @DisplayName("sets state to INITIATED")
        void setsStateToInitiated() {
            var payment = anInitiatedPayment();

            assertThat(payment.state()).isEqualTo(INITIATED);
        }

        @Test
        @DisplayName("sets expiresAt to 30 minutes after createdAt")
        void setsExpiresAt() {
            var payment = anInitiatedPayment();

            assertThat(payment.expiresAt()).isAfter(payment.createdAt());
            assertThat(payment.expiresAt()).isBefore(payment.createdAt().plusSeconds(1801));
        }

        @Test
        @DisplayName("sets empty metadata map")
        void setsEmptyMetadata() {
            var payment = anInitiatedPayment();

            assertThat(payment.metadata()).isEqualTo(Map.of());
        }

        @Test
        @DisplayName("rejects null idempotencyKey")
        void rejectsNullIdempotencyKey() {
            assertThatThrownBy(() -> Payment.initiate(
                    null, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idempotencyKey is required");
        }

        @Test
        @DisplayName("rejects blank idempotencyKey")
        void rejectsBlankIdempotencyKey() {
            assertThatThrownBy(() -> Payment.initiate(
                    "  ", CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idempotencyKey is required");
        }

        @Test
        @DisplayName("rejects null correlationId")
        void rejectsNullCorrelationId() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, null, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("correlationId is required");
        }

        @Test
        @DisplayName("rejects null senderId")
        void rejectsNullSenderId() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, null, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("senderId is required");
        }

        @Test
        @DisplayName("rejects null recipientId")
        void rejectsNullRecipientId() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, null,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("recipientId is required");
        }

        @Test
        @DisplayName("rejects null sourceAmount")
        void rejectsNullSourceAmount() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    null, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sourceAmount is required");
        }

        @Test
        @DisplayName("rejects null sourceCurrency")
        void rejectsNullSourceCurrency() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, null, TARGET_CURRENCY, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sourceCurrency is required");
        }

        @Test
        @DisplayName("rejects null targetCurrency")
        void rejectsNullTargetCurrency() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, null, US_TO_DE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("targetCurrency is required");
        }

        @Test
        @DisplayName("rejects null corridor")
        void rejectsNullCorridor() {
            assertThatThrownBy(() -> Payment.initiate(
                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("corridor is required");
        }
    }

    // ── Happy Path Transitions ────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path Transitions")
    class HappyPathTransitions {

        @Test
        @DisplayName("INITIATED → COMPLIANCE_CHECK via startComplianceCheck()")
        void initiatedToComplianceCheck() {
            var payment = anInitiatedPayment();

            var result = payment.startComplianceCheck();

            assertThat(result.state()).isEqualTo(COMPLIANCE_CHECK);
        }

        @Test
        @DisplayName("COMPLIANCE_CHECK → FX_LOCKED via lockFxRate()")
        void complianceCheckToFxLocked() {
            var fxRate = aValidFxRate();
            var payment = aComplianceCheckPayment();

            var result = payment.lockFxRate(fxRate);

            var expectedTargetAmount = new Money(
                    SOURCE_AMOUNT.amount().multiply(fxRate.rate()),
                    TARGET_CURRENCY
            );

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("paymentId", "createdAt", "updatedAt", "expiresAt")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(Payment.initiate(
                                    IDEMPOTENCY_KEY, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                                    SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE)
                            .startComplianceCheck()
                            .lockFxRate(fxRate));
        }

        @Test
        @DisplayName("lockFxRate calculates target amount correctly")
        void lockFxRateCalculatesTargetAmount() {
            var fxRate = aValidFxRate();
            var payment = aComplianceCheckPayment();

            var result = payment.lockFxRate(fxRate);

            var expectedAmount = SOURCE_AMOUNT.amount().multiply(fxRate.rate());
            assertThat(result.targetAmount().amount()).isEqualByComparingTo(expectedAmount);
        }

        @Test
        @DisplayName("lockFxRate stores the FX rate on the payment")
        void lockFxRateStoresFxRate() {
            var fxRate = aValidFxRate();
            var payment = aComplianceCheckPayment();

            var result = payment.lockFxRate(fxRate);

            assertThat(result.lockedFxRate()).isEqualTo(fxRate);
        }

        @Test
        @DisplayName("FX_LOCKED → FIAT_COLLECTION_PENDING via startFiatCollection()")
        void fxLockedToFiatCollectionPending() {
            var result = anFxLockedPayment().startFiatCollection();

            assertThat(result.state()).isEqualTo(FIAT_COLLECTION_PENDING);
        }

        @Test
        @DisplayName("FIAT_COLLECTION_PENDING → FIAT_COLLECTED via confirmFiatCollected()")
        void fiatCollectionPendingToFiatCollected() {
            var result = aFiatCollectionPendingPayment().confirmFiatCollected();

            assertThat(result.state()).isEqualTo(FIAT_COLLECTED);
        }

        @Test
        @DisplayName("FIAT_COLLECTED → ON_CHAIN_SUBMITTED via submitOnChain()")
        void fiatCollectedToOnChainSubmitted() {
            var result = aFiatCollectedPayment().submitOnChain(BASE_CHAIN);

            assertThat(result.state()).isEqualTo(ON_CHAIN_SUBMITTED);
        }

        @Test
        @DisplayName("submitOnChain stores the chain ID")
        void submitOnChainStoresChainId() {
            var result = aFiatCollectedPayment().submitOnChain(BASE_CHAIN);

            assertThat(result.chainSelected()).isEqualTo(BASE_CHAIN);
        }

        @Test
        @DisplayName("ON_CHAIN_SUBMITTED → ON_CHAIN_CONFIRMED via confirmOnChain()")
        void onChainSubmittedToOnChainConfirmed() {
            var result = anOnChainSubmittedPayment().confirmOnChain(TX_HASH);

            assertThat(result.state()).isEqualTo(ON_CHAIN_CONFIRMED);
        }

        @Test
        @DisplayName("confirmOnChain stores the transaction hash")
        void confirmOnChainStoresTxHash() {
            var result = anOnChainSubmittedPayment().confirmOnChain(TX_HASH);

            assertThat(result.txHash()).isEqualTo(TX_HASH);
        }

        @Test
        @DisplayName("ON_CHAIN_CONFIRMED → OFF_RAMP_INITIATED via initiateOffRamp()")
        void onChainConfirmedToOffRampInitiated() {
            var result = anOnChainConfirmedPayment().initiateOffRamp();

            assertThat(result.state()).isEqualTo(OFF_RAMP_INITIATED);
        }

        @Test
        @DisplayName("OFF_RAMP_INITIATED → SETTLED via settle()")
        void offRampInitiatedToSettled() {
            var result = anOffRampInitiatedPayment().settle();

            assertThat(result.state()).isEqualTo(SETTLED);
        }

        @Test
        @DisplayName("SETTLED → COMPLETED via complete()")
        void settledToCompleted() {
            var result = aSettledPayment().complete();

            assertThat(result.state()).isEqualTo(COMPLETED);
        }

        @Test
        @DisplayName("full happy path walks through all 10 states")
        void fullHappyPath() {
            var result = aCompletedPayment();

            assertThat(result.state()).isEqualTo(COMPLETED);
        }

        @Test
        @DisplayName("each transition updates updatedAt")
        void transitionUpdatesTimestamp() {
            var initiated = anInitiatedPayment();
            var compliance = initiated.startComplianceCheck();

            assertThat(compliance.updatedAt()).isAfterOrEqualTo(initiated.updatedAt());
        }
    }

    // ── Failure Transitions ──────────────────────────────────────────

    @Nested
    @DisplayName("Failure Transitions")
    class FailureTransitions {

        @ParameterizedTest(name = "fail() from {0}")
        @MethodSource("failableStates")
        @DisplayName("fail() transitions to FAILED from non-terminal active states")
        void failFromActiveState(String stateName, Payment payment) {
            var result = payment.fail("something went wrong");

            assertThat(result.state()).isEqualTo(FAILED);
        }

        @ParameterizedTest(name = "fail() stores reason from {0}")
        @MethodSource("failableStates")
        @DisplayName("fail() stores the failure reason")
        void failStoresReason(String stateName, Payment payment) {
            var result = payment.fail("something went wrong");

            assertThat(result.failureReason()).isEqualTo("something went wrong");
        }

        static Stream<Arguments> failableStates() {
            return Stream.of(
                    Arguments.of("INITIATED", anInitiatedPayment()),
                    Arguments.of("COMPLIANCE_CHECK", aComplianceCheckPayment()),
                    Arguments.of("FX_LOCKED", anFxLockedPayment()),
                    Arguments.of("FIAT_COLLECTION_PENDING", aFiatCollectionPendingPayment())
            );
        }
    }

    // ── Compensation Transitions ─────────────────────────────────────

    @Nested
    @DisplayName("Compensation Transitions")
    class CompensationTransitions {

        @Test
        @DisplayName("FIAT_COLLECTED → COMPENSATING_FIAT_REFUND via startCompensation()")
        void fiatCollectedToCompensatingFiatRefund() {
            var result = aFiatCollectedPayment().startCompensation("Refund needed");

            assertThat(result.state()).isEqualTo(COMPENSATING_FIAT_REFUND);
        }

        @Test
        @DisplayName("ON_CHAIN_SUBMITTED → COMPENSATING_STABLECOIN_RETURN via startCompensation()")
        void onChainSubmittedToCompensatingStablecoinReturn() {
            var result = anOnChainSubmittedPayment().startCompensation("Return needed");

            assertThat(result.state()).isEqualTo(COMPENSATING_STABLECOIN_RETURN);
        }

        @Test
        @DisplayName("ON_CHAIN_CONFIRMED → COMPENSATING_STABLECOIN_RETURN via startCompensation()")
        void onChainConfirmedToCompensatingStablecoinReturn() {
            var result = anOnChainConfirmedPayment().startCompensation("Return needed");

            assertThat(result.state()).isEqualTo(COMPENSATING_STABLECOIN_RETURN);
        }

        @Test
        @DisplayName("OFF_RAMP_INITIATED → COMPENSATING_STABLECOIN_RETURN via startCompensation()")
        void offRampInitiatedToCompensatingStablecoinReturn() {
            var result = anOffRampInitiatedPayment().startCompensation("Return needed");

            assertThat(result.state()).isEqualTo(COMPENSATING_STABLECOIN_RETURN);
        }

        @Test
        @DisplayName("startCompensation stores the compensation reason")
        void startCompensationStoresReason() {
            var result = aFiatCollectedPayment().startCompensation("Refund needed");

            assertThat(result.failureReason()).isEqualTo("Refund needed");
        }

        @Test
        @DisplayName("COMPENSATING_FIAT_REFUND → FAILED via fail()")
        void compensatingFiatRefundToFailed() {
            var result = aCompensatingFiatRefundPayment().fail("Refund completed");

            assertThat(result.state()).isEqualTo(FAILED);
        }

        @Test
        @DisplayName("COMPENSATING_STABLECOIN_RETURN → FAILED via fail()")
        void compensatingStablecoinReturnToFailed() {
            var result = aCompensatingStablecoinReturnPayment().fail("Return completed");

            assertThat(result.state()).isEqualTo(FAILED);
        }

        @Test
        @DisplayName("startCompensation rejects null reason")
        void rejectsNullReason() {
            assertThatThrownBy(() -> aFiatCollectedPayment().startCompensation(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Compensation reason is required");
        }

        @Test
        @DisplayName("startCompensation rejects blank reason")
        void rejectsBlankReason() {
            assertThatThrownBy(() -> aFiatCollectedPayment().startCompensation("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Compensation reason is required");
        }
    }

    // ── Terminal State Guard ─────────────────────────────────────────

    @Nested
    @DisplayName("Terminal State Guard")
    class TerminalStateGuard {

        @Test
        @DisplayName("COMPLETED rejects startComplianceCheck()")
        void completedRejectsStartCompliance() {
            var completed = aCompletedPayment();

            assertThatThrownBy(completed::startComplianceCheck)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("COMPLETED rejects lockFxRate()")
        void completedRejectsLockFxRate() {
            var completed = aCompletedPayment();

            assertThatThrownBy(() -> completed.lockFxRate(aValidFxRate()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("COMPLETED rejects startFiatCollection()")
        void completedRejectsStartFiatCollection() {
            var completed = aCompletedPayment();

            assertThatThrownBy(completed::startFiatCollection)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("COMPLETED rejects settle()")
        void completedRejectsSettle() {
            var completed = aCompletedPayment();

            assertThatThrownBy(completed::settle)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("COMPLETED rejects complete()")
        void completedRejectsComplete() {
            var completed = aCompletedPayment();

            assertThatThrownBy(completed::complete)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("COMPLETED rejects startCompensation()")
        void completedRejectsStartCompensation() {
            var completed = aCompletedPayment();

            assertThatThrownBy(() -> completed.startCompensation("reason"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects startComplianceCheck()")
        void failedRejectsStartCompliance() {
            var failed = aFailedPayment();

            assertThatThrownBy(failed::startComplianceCheck)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects startCompensation()")
        void failedRejectsStartCompensation() {
            var failed = aFailedPayment();

            assertThatThrownBy(() -> failed.startCompensation("reason"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }
    }

    // ── Invalid Transitions ──────────────────────────────────────────

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @ParameterizedTest(name = "{0} rejects {1}")
        @MethodSource("invalidTransitions")
        @DisplayName("invalid transitions throw StateMachineException")
        void invalidTransitionThrowsStateMachineException(String description,
                                                          Payment payment,
                                                          PaymentTransitionAction action) {
            assertThatThrownBy(() -> action.execute(payment))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("Invalid transition");
        }

        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    // INITIATED only allows START_COMPLIANCE and FAIL
                    Arguments.of("INITIATED → lockFxRate",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) p -> p.lockFxRate(aValidFxRate())),
                    Arguments.of("INITIATED → startFiatCollection",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) Payment::startFiatCollection),
                    Arguments.of("INITIATED → confirmFiatCollected",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) Payment::confirmFiatCollected),
                    Arguments.of("INITIATED → submitOnChain",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) p -> p.submitOnChain(BASE_CHAIN)),
                    Arguments.of("INITIATED → confirmOnChain",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) p -> p.confirmOnChain(TX_HASH)),
                    Arguments.of("INITIATED → settle",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) Payment::settle),
                    Arguments.of("INITIATED → complete",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) Payment::complete),
                    Arguments.of("INITIATED → startCompensation",
                            anInitiatedPayment(),
                            (PaymentTransitionAction) p -> p.startCompensation("reason")),

                    // COMPLIANCE_CHECK only allows COMPLIANCE_PASSED (lockFxRate) and FAIL
                    Arguments.of("COMPLIANCE_CHECK → startComplianceCheck",
                            aComplianceCheckPayment(),
                            (PaymentTransitionAction) Payment::startComplianceCheck),
                    Arguments.of("COMPLIANCE_CHECK → startFiatCollection",
                            aComplianceCheckPayment(),
                            (PaymentTransitionAction) Payment::startFiatCollection),

                    // FX_LOCKED only allows LOCK_FX (startFiatCollection) and FAIL
                    Arguments.of("FX_LOCKED → startComplianceCheck",
                            anFxLockedPayment(),
                            (PaymentTransitionAction) Payment::startComplianceCheck),
                    Arguments.of("FX_LOCKED → lockFxRate",
                            anFxLockedPayment(),
                            (PaymentTransitionAction) p -> p.lockFxRate(aValidFxRate())),

                    // FIAT_COLLECTED does not allow FAIL (only compensation)
                    Arguments.of("FIAT_COLLECTED → startComplianceCheck",
                            aFiatCollectedPayment(),
                            (PaymentTransitionAction) Payment::startComplianceCheck),

                    // ON_CHAIN_SUBMITTED does not allow FAIL (only compensation)
                    Arguments.of("ON_CHAIN_SUBMITTED → settle",
                            anOnChainSubmittedPayment(),
                            (PaymentTransitionAction) Payment::settle),

                    // SETTLED only allows COMPLETE
                    Arguments.of("SETTLED → startComplianceCheck",
                            aSettledPayment(),
                            (PaymentTransitionAction) Payment::startComplianceCheck),
                    Arguments.of("SETTLED → startCompensation",
                            aSettledPayment(),
                            (PaymentTransitionAction) p -> p.startCompensation("reason"))
            );
        }
    }

    // ── Input Validation on Transition Methods ───────────────────────

    @Nested
    @DisplayName("Input Validation on Transition Methods")
    class InputValidation {

        @Test
        @DisplayName("lockFxRate rejects null FX rate")
        void lockFxRateRejectsNull() {
            assertThatThrownBy(() -> aComplianceCheckPayment().lockFxRate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FX rate is required");
        }

        @Test
        @DisplayName("submitOnChain rejects null chain ID")
        void submitOnChainRejectsNull() {
            assertThatThrownBy(() -> aFiatCollectedPayment().submitOnChain(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Chain ID is required");
        }

        @Test
        @DisplayName("confirmOnChain rejects null transaction hash")
        void confirmOnChainRejectsNullHash() {
            assertThatThrownBy(() -> anOnChainSubmittedPayment().confirmOnChain(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required");
        }

        @Test
        @DisplayName("confirmOnChain rejects blank transaction hash")
        void confirmOnChainRejectsBlankHash() {
            assertThatThrownBy(() -> anOnChainSubmittedPayment().confirmOnChain("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required");
        }
    }

    // ── Query Methods ────────────────────────────────────────────────

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("isTerminal returns true for COMPLETED")
        void isTerminalForCompleted() {
            assertThat(aCompletedPayment().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("isTerminal returns true for FAILED")
        void isTerminalForFailed() {
            assertThat(aFailedPayment().isTerminal()).isTrue();
        }

        @ParameterizedTest(name = "isTerminal returns false for {0}")
        @MethodSource("nonTerminalStates")
        @DisplayName("isTerminal returns false for non-terminal states")
        void isTerminalReturnsFalseForNonTerminal(String stateName, Payment payment) {
            assertThat(payment.isTerminal()).isFalse();
        }

        static Stream<Arguments> nonTerminalStates() {
            return Stream.of(
                    Arguments.of("INITIATED", anInitiatedPayment()),
                    Arguments.of("COMPLIANCE_CHECK", aComplianceCheckPayment()),
                    Arguments.of("FX_LOCKED", anFxLockedPayment()),
                    Arguments.of("FIAT_COLLECTION_PENDING", aFiatCollectionPendingPayment()),
                    Arguments.of("FIAT_COLLECTED", aFiatCollectedPayment()),
                    Arguments.of("ON_CHAIN_SUBMITTED", anOnChainSubmittedPayment()),
                    Arguments.of("ON_CHAIN_CONFIRMED", anOnChainConfirmedPayment()),
                    Arguments.of("OFF_RAMP_INITIATED", anOffRampInitiatedPayment()),
                    Arguments.of("SETTLED", aSettledPayment()),
                    Arguments.of("COMPENSATING_FIAT_REFUND", aCompensatingFiatRefundPayment()),
                    Arguments.of("COMPENSATING_STABLECOIN_RETURN", aCompensatingStablecoinReturnPayment())
            );
        }

        @Test
        @DisplayName("canApply returns true for valid transition")
        void canApplyReturnsTrueForValidTransition() {
            assertThat(anInitiatedPayment().canApply(START_COMPLIANCE)).isTrue();
        }

        @Test
        @DisplayName("canApply returns false for invalid transition")
        void canApplyReturnsFalseForInvalidTransition() {
            assertThat(anInitiatedPayment().canApply(COMPLETE)).isFalse();
        }

        @Test
        @DisplayName("canApply returns true for FAIL from INITIATED")
        void canApplyFailFromInitiated() {
            assertThat(anInitiatedPayment().canApply(FAIL)).isTrue();
        }

        @Test
        @DisplayName("canApply returns true for START_COMPENSATION from FIAT_COLLECTED")
        void canApplyCompensationFromFiatCollected() {
            assertThat(aFiatCollectedPayment().canApply(START_COMPENSATION)).isTrue();
        }

        @Test
        @DisplayName("isCompensating returns true for COMPENSATING_FIAT_REFUND")
        void isCompensatingForFiatRefund() {
            assertThat(aCompensatingFiatRefundPayment().isCompensating()).isTrue();
        }

        @Test
        @DisplayName("isCompensating returns true for COMPENSATING_STABLECOIN_RETURN")
        void isCompensatingForStablecoinReturn() {
            assertThat(aCompensatingStablecoinReturnPayment().isCompensating()).isTrue();
        }

        @ParameterizedTest(name = "isCompensating returns false for {0}")
        @MethodSource("nonCompensatingStates")
        @DisplayName("isCompensating returns false for non-compensation states")
        void isCompensatingReturnsFalseForNonCompensation(String stateName, Payment payment) {
            assertThat(payment.isCompensating()).isFalse();
        }

        static Stream<Arguments> nonCompensatingStates() {
            return Stream.of(
                    Arguments.of("INITIATED", anInitiatedPayment()),
                    Arguments.of("COMPLIANCE_CHECK", aComplianceCheckPayment()),
                    Arguments.of("FX_LOCKED", anFxLockedPayment()),
                    Arguments.of("COMPLETED", aCompletedPayment()),
                    Arguments.of("FAILED", aFailedPayment())
            );
        }
    }

    // ── Functional interface for parameterized invalid transitions ───

    @FunctionalInterface
    interface PaymentTransitionAction {
        Payment execute(Payment payment);
    }
}
