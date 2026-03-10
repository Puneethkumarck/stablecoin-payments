package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutInitiatedEvent;
import com.stablecoin.payments.offramp.domain.event.StablecoinRedeemedEvent;
import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.model.BankAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyAccount;
import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.domain.model.PartnerIdentifier;
import com.stablecoin.payments.offramp.domain.model.PaymentRail;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutType;
import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;
import com.stablecoin.payments.offramp.domain.model.StablecoinTicker;
import com.stablecoin.payments.offramp.domain.port.OffRampTransactionRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutPartnerGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionRequest;
import com.stablecoin.payments.offramp.domain.port.StablecoinRedemptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain command handler for payout order operations.
 * <p>
 * Orchestrates: idempotency check → create order → redeem stablecoin →
 * initiate partner payout → record audit trail → publish events.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PayoutCommandHandler {

    private final PayoutOrderRepository payoutOrderRepository;
    private final StablecoinRedemptionRepository stablecoinRedemptionRepository;
    private final OffRampTransactionRepository offRampTransactionRepository;
    private final RedemptionGateway redemptionGateway;
    private final PayoutPartnerGateway payoutPartnerGateway;
    private final PayoutEventPublisher eventPublisher;

    /**
     * Initiates a new payout order for a payment.
     * <p>
     * Idempotent: if a payout order already exists for the given paymentId,
     * returns the existing order with {@code created = false}.
     * <p>
     * For HOLD_STABLECOIN type: skips redemption and payout, transitions directly
     * to STABLECOIN_HELD → COMPLETED.
     * <p>
     * For FIAT type: redeems stablecoin via RedemptionGateway, then initiates
     * fiat payout via PayoutPartnerGateway.
     */
    public PayoutResult initiatePayout(UUID paymentId, UUID correlationId, UUID transferId,
                                       PayoutType payoutType, StablecoinTicker stablecoin,
                                       BigDecimal redeemedAmount, String targetCurrency,
                                       BigDecimal appliedFxRate, UUID recipientId,
                                       String recipientAccountHash,
                                       BankAccount bankAccount, MobileMoneyAccount mobileMoneyAccount,
                                       PaymentRail paymentRail, PartnerIdentifier offRampPartner) {
        // 1. Idempotency: check if payout order already exists for this paymentId
        var existing = payoutOrderRepository.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            log.info("Payout order already exists for paymentId={} payoutId={} status={}",
                    paymentId, existing.get().payoutId(), existing.get().status());
            return new PayoutResult(existing.get(), false);
        }

        // 2. Create new payout order in PENDING state
        var order = PayoutOrder.create(paymentId, correlationId, transferId,
                payoutType, stablecoin, redeemedAmount, targetCurrency,
                appliedFxRate, recipientId, recipientAccountHash,
                bankAccount, mobileMoneyAccount, paymentRail, offRampPartner);

        // 3. HOLD_STABLECOIN path: skip redemption and payout
        if (payoutType == PayoutType.HOLD_STABLECOIN) {
            order = order.holdStablecoin().completeHold();
            order = payoutOrderRepository.save(order);
            log.info("Payout held as stablecoin payoutId={} paymentId={}",
                    order.payoutId(), paymentId);
            return new PayoutResult(order, true);
        }

        // 4. FIAT path: redeem stablecoin
        order = order.startRedemption();
        var redemptionResult = redemptionGateway.redeem(new RedemptionRequest(
                order.payoutId(), stablecoin.ticker(), redeemedAmount));

        // 5. Complete redemption with fiat amount
        order = order.completeRedemption(redemptionResult.fiatReceived());

        // 6. Record StablecoinRedemption
        var redemption = StablecoinRedemption.create(
                order.payoutId(), stablecoin, redeemedAmount,
                redemptionResult.fiatReceived(), redemptionResult.fiatCurrency(),
                offRampPartner.partnerName(), redemptionResult.partnerReference());
        stablecoinRedemptionRepository.save(redemption);

        // 7. Publish StablecoinRedeemedEvent via outbox
        eventPublisher.publish(new StablecoinRedeemedEvent(
                redemption.redemptionId(),
                order.payoutId(),
                paymentId,
                correlationId,
                stablecoin.ticker(),
                redeemedAmount,
                redemptionResult.fiatReceived(),
                redemptionResult.fiatCurrency(),
                redemptionResult.redeemedAt()));

        // 8. Initiate fiat payout with partner
        var payoutResult = payoutPartnerGateway.initiatePayout(
                new com.stablecoin.payments.offramp.domain.port.PayoutRequest(
                        order.payoutId(),
                        order.fiatAmount(),
                        targetCurrency,
                        bankAccount,
                        mobileMoneyAccount,
                        paymentRail,
                        offRampPartner));

        // 9. Transition to PAYOUT_INITIATED
        order = order.initiatePayout(payoutResult.partnerReference());

        // 10. Record OffRampTransaction for audit trail
        var offRampTxn = OffRampTransaction.create(
                order.payoutId(),
                offRampPartner.partnerName(),
                "payout.initiated",
                order.fiatAmount(),
                targetCurrency,
                payoutResult.status(),
                null);
        offRampTransactionRepository.save(offRampTxn);

        // 11. Save payout order (final state)
        order = payoutOrderRepository.save(order);

        // 12. Publish FiatPayoutInitiatedEvent via outbox
        eventPublisher.publish(new FiatPayoutInitiatedEvent(
                order.payoutId(),
                paymentId,
                correlationId,
                order.fiatAmount(),
                targetCurrency,
                paymentRail.name(),
                offRampPartner.partnerName(),
                Instant.now()));

        log.info("Payout initiated payoutId={} paymentId={} partnerRef={}",
                order.payoutId(), paymentId, payoutResult.partnerReference());

        return new PayoutResult(order, true);
    }

    /**
     * Retrieves a payout order by its ID.
     *
     * @param payoutId the payout order identifier
     * @return the payout order
     * @throws PayoutNotFoundException if the order is not found
     */
    public PayoutOrder getPayout(UUID payoutId) {
        return payoutOrderRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutNotFoundException(payoutId));
    }

    /**
     * Retrieves a payout order by its associated payment ID.
     *
     * @param paymentId the payment identifier
     * @return the payout order
     * @throws PayoutNotFoundException if the order is not found
     */
    public PayoutOrder getPayoutByPaymentId(UUID paymentId) {
        return payoutOrderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PayoutNotFoundException(paymentId.toString()));
    }
}
