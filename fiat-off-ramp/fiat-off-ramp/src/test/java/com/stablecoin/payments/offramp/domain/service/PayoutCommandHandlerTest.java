package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutInitiatedEvent;
import com.stablecoin.payments.offramp.domain.event.StablecoinRedeemedEvent;
import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.model.PayoutType;
import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;
import com.stablecoin.payments.offramp.domain.port.OffRampTransactionRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutPartnerGateway;
import com.stablecoin.payments.offramp.domain.port.PayoutResult;
import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionRequest;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import com.stablecoin.payments.offramp.domain.port.StablecoinRedemptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.APPLIED_FX_RATE;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.CORRELATION_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.EXPECTED_FIAT_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.RECIPIENT_ACCOUNT_HASH;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.REDEEMED_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.TRANSFER_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aBankAccount;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPartnerIdentifier;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinTicker;
import static com.stablecoin.payments.offramp.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.offramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutCommandHandler")
class PayoutCommandHandlerTest {

    private static final String REDEMPTION_PARTNER_REF = "circle_redeem_ref_001";
    private static final String PAYOUT_PARTNER_REF = "modulr_payout_ref_001";
    private static final String FIAT_CURRENCY = "EUR";
    private static final Instant REDEEMED_AT = Instant.parse("2026-03-10T10:00:00Z");

    @Mock
    private PayoutOrderRepository orderRepository;

    @Mock
    private StablecoinRedemptionRepository redemptionRepository;

    @Mock
    private OffRampTransactionRepository transactionRepository;

    @Mock
    private RedemptionGateway redemptionGateway;

    @Mock
    private PayoutPartnerGateway payoutPartnerGateway;

    @Mock
    private PayoutEventPublisher eventPublisher;

    @InjectMocks
    private PayoutCommandHandler handler;

    @Nested
    @DisplayName("initiatePayout — FIAT happy path")
    class FiatHappyPath {

        @Test
        @DisplayName("should create order, redeem stablecoin, initiate payout, and publish events")
        void shouldCreateOrderRedeemAndInitiatePayout() {
            // Build expected final state: PENDING → REDEEMING → REDEEMED → PAYOUT_INITIATED
            var pending = aPendingOrder();
            var expectedFinal = pending.startRedemption()
                    .completeRedemption(EXPECTED_FIAT_AMOUNT)
                    .initiatePayout(PAYOUT_PARTNER_REF);

            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());
            given(redemptionGateway.redeem(eqIgnoring(
                    new RedemptionRequest(pending.payoutId(), "USDC", REDEEMED_AMOUNT),
                    "payoutId")))
                    .willReturn(new RedemptionResult(
                            REDEMPTION_PARTNER_REF, EXPECTED_FIAT_AMOUNT, FIAT_CURRENCY, REDEEMED_AT));
            given(payoutPartnerGateway.initiatePayout(eqIgnoring(
                    new com.stablecoin.payments.offramp.domain.port.PayoutRequest(
                            pending.payoutId(),
                            EXPECTED_FIAT_AMOUNT,
                            TARGET_CURRENCY,
                            aBankAccount(),
                            null,
                            com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                            aPartnerIdentifier()),
                    "payoutId")))
                    .willReturn(new PayoutResult(PAYOUT_PARTNER_REF, "INITIATED", null));
            given(orderRepository.save(eqIgnoring(expectedFinal, "payoutId")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.initiatePayout(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY, APPLIED_FX_RATE,
                    RECIPIENT_ID, RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                    aPartnerIdentifier());

            then(orderRepository).should().save(eqIgnoring(expectedFinal, "payoutId"));
        }

        @Test
        @DisplayName("should save StablecoinRedemption record")
        void shouldSaveRedemptionRecord() {
            stubFiatHappyPath();

            callInitiatePayout();

            var expectedRedemption = StablecoinRedemption.create(
                    UUID.randomUUID(), aStablecoinTicker(), REDEEMED_AMOUNT,
                    EXPECTED_FIAT_AMOUNT, FIAT_CURRENCY,
                    aPartnerIdentifier().partnerName(), REDEMPTION_PARTNER_REF);
            then(redemptionRepository).should().save(eqIgnoring(expectedRedemption, "redemptionId", "payoutId"));
        }

        @Test
        @DisplayName("should save OffRampTransaction audit record")
        void shouldSaveOffRampTransactionRecord() {
            stubFiatHappyPath();

            callInitiatePayout();

            var expectedTxn = OffRampTransaction.create(
                    UUID.randomUUID(), aPartnerIdentifier().partnerName(),
                    "payout.initiated", EXPECTED_FIAT_AMOUNT, TARGET_CURRENCY,
                    "INITIATED", null);
            then(transactionRepository).should().save(eqIgnoring(expectedTxn, "offRampTxnId", "payoutId"));
        }

        @Test
        @DisplayName("should publish StablecoinRedeemedEvent and FiatPayoutInitiatedEvent")
        void shouldPublishEvents() {
            stubFiatHappyPath();

            callInitiatePayout();

            var expectedRedeemedEvent = new StablecoinRedeemedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), PAYMENT_ID, CORRELATION_ID,
                    "USDC", REDEEMED_AMOUNT, EXPECTED_FIAT_AMOUNT, FIAT_CURRENCY, REDEEMED_AT);
            then(eventPublisher).should().publish(eqIgnoring(
                    expectedRedeemedEvent, "redemptionId", "payoutId"));

            var expectedInitiatedEvent = new FiatPayoutInitiatedEvent(
                    UUID.randomUUID(), PAYMENT_ID, CORRELATION_ID,
                    EXPECTED_FIAT_AMOUNT, TARGET_CURRENCY, "SEPA",
                    aPartnerIdentifier().partnerName(), Instant.now());
            then(eventPublisher).should().publish(eqIgnoring(
                    expectedInitiatedEvent, "payoutId"));
        }

        @Test
        @DisplayName("should return created=true for new payout")
        void shouldReturnCreatedTrue() {
            stubFiatHappyPath();

            var result = callInitiatePayout();

            assertThat(result.created()).isTrue();
            assertThat(result.order().status()).isEqualTo(PayoutStatus.PAYOUT_INITIATED);
        }

        private void stubFiatHappyPath() {
            var expectedFinal = aPendingOrder().startRedemption()
                    .completeRedemption(EXPECTED_FIAT_AMOUNT)
                    .initiatePayout(PAYOUT_PARTNER_REF);

            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());
            given(redemptionGateway.redeem(eqIgnoring(
                    new RedemptionRequest(UUID.randomUUID(), "USDC", REDEEMED_AMOUNT),
                    "payoutId")))
                    .willReturn(new RedemptionResult(
                            REDEMPTION_PARTNER_REF, EXPECTED_FIAT_AMOUNT, FIAT_CURRENCY, REDEEMED_AT));
            given(payoutPartnerGateway.initiatePayout(eqIgnoring(
                    new com.stablecoin.payments.offramp.domain.port.PayoutRequest(
                            UUID.randomUUID(), EXPECTED_FIAT_AMOUNT, TARGET_CURRENCY,
                            aBankAccount(), null,
                            com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                            aPartnerIdentifier()),
                    "payoutId")))
                    .willReturn(new PayoutResult(PAYOUT_PARTNER_REF, "INITIATED", null));
            given(orderRepository.save(eqIgnoring(expectedFinal, "payoutId")))
                    .willAnswer(inv -> inv.getArgument(0));
        }

        private com.stablecoin.payments.offramp.domain.service.PayoutResult callInitiatePayout() {
            return handler.initiatePayout(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY, APPLIED_FX_RATE,
                    RECIPIENT_ID, RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                    aPartnerIdentifier());
        }
    }

    @Nested
    @DisplayName("initiatePayout — idempotency")
    class Idempotency {

        @Test
        @DisplayName("should return existing order with created=false when paymentId already exists")
        void shouldReturnExistingOrder() {
            var existing = aPendingOrder();

            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(existing));

            var result = handler.initiatePayout(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY, APPLIED_FX_RATE,
                    RECIPIENT_ID, RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                    aPartnerIdentifier());

            assertThat(result.created()).isFalse();
            assertThat(result.order()).isEqualTo(existing);
            then(redemptionGateway).shouldHaveNoInteractions();
            then(payoutPartnerGateway).shouldHaveNoInteractions();
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(existing));
        }
    }

    @Nested
    @DisplayName("initiatePayout — HOLD_STABLECOIN path")
    class HoldStablecoin {

        @Test
        @DisplayName("should skip redemption and payout, transition to COMPLETED via STABLECOIN_HELD")
        void shouldHoldStablecoinAndComplete() {
            var expectedOrder = PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.HOLD_STABLECOIN, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY, APPLIED_FX_RATE,
                    RECIPIENT_ID, RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                    aPartnerIdentifier()).holdStablecoin().completeHold();

            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());
            given(orderRepository.save(eqIgnoring(expectedOrder, "payoutId")))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = handler.initiatePayout(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.HOLD_STABLECOIN, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY, APPLIED_FX_RATE,
                    RECIPIENT_ID, RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA,
                    aPartnerIdentifier());

            assertThat(result.created()).isTrue();
            assertThat(result.order().status()).isEqualTo(PayoutStatus.COMPLETED);
            then(orderRepository).should().save(eqIgnoring(expectedOrder, "payoutId"));
            then(redemptionGateway).shouldHaveNoInteractions();
            then(payoutPartnerGateway).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getPayout")
    class GetPayout {

        @Test
        @DisplayName("should return payout order by ID")
        void shouldReturnPayoutById() {
            var order = aPendingOrder();
            given(orderRepository.findById(order.payoutId()))
                    .willReturn(Optional.of(order));

            var result = handler.getPayout(order.payoutId());

            assertThat(result).isEqualTo(order);
        }

        @Test
        @DisplayName("should throw PayoutNotFoundException when not found by ID")
        void shouldThrowWhenNotFoundById() {
            var payoutId = UUID.randomUUID();
            given(orderRepository.findById(payoutId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getPayout(payoutId))
                    .isInstanceOf(PayoutNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPayoutByPaymentId")
    class GetPayoutByPaymentId {

        @Test
        @DisplayName("should return payout order by payment ID")
        void shouldReturnPayoutByPaymentId() {
            var order = aPendingOrder();
            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(order));

            var result = handler.getPayoutByPaymentId(PAYMENT_ID);

            assertThat(result).isEqualTo(order);
        }

        @Test
        @DisplayName("should throw PayoutNotFoundException when not found by payment ID")
        void shouldThrowWhenNotFoundByPaymentId() {
            given(orderRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getPayoutByPaymentId(PAYMENT_ID))
                    .isInstanceOf(PayoutNotFoundException.class);
        }
    }
}
