package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.api.PayoutRequest;
import com.stablecoin.payments.offramp.api.PayoutResponse;
import com.stablecoin.payments.offramp.domain.model.AccountType;
import com.stablecoin.payments.offramp.domain.model.BankAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyProvider;
import com.stablecoin.payments.offramp.domain.model.PartnerIdentifier;
import com.stablecoin.payments.offramp.domain.model.PaymentRail;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutType;
import com.stablecoin.payments.offramp.domain.model.StablecoinTicker;
import com.stablecoin.payments.offramp.domain.service.PayoutCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for payout order lifecycle management.
 * <p>
 * Thin HTTP handler that delegates all business logic to {@link PayoutCommandHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutCommandHandler payoutCommandHandler;

    /**
     * Initiates a new payout order.
     * <p>
     * Idempotent: returns 200 OK with the existing order if the same paymentId
     * is submitted again. Returns 202 ACCEPTED on first creation.
     */
    @PostMapping
    public ResponseEntity<PayoutResponse> initiatePayout(
            @Valid @RequestBody PayoutRequest request) {
        log.info("POST /v1/payouts paymentId={}", request.paymentId());

        var stablecoin = StablecoinTicker.of(request.stablecoin());
        var payoutType = PayoutType.valueOf(request.payoutType());
        var paymentRail = PaymentRail.valueOf(request.paymentRail());
        var partner = new PartnerIdentifier(request.offRampPartnerId(), request.offRampPartnerName());
        var bankAccount = buildBankAccount(request);
        var mobileMoneyAccount = buildMobileMoneyAccount(request);

        var result = payoutCommandHandler.initiatePayout(
                request.paymentId(),
                request.correlationId(),
                request.transferId(),
                payoutType,
                stablecoin,
                request.redeemedAmount(),
                request.targetCurrency(),
                request.appliedFxRate(),
                request.recipientId(),
                request.recipientAccountHash(),
                bankAccount,
                mobileMoneyAccount,
                paymentRail,
                partner);

        var response = toPayoutResponse(result.order());
        var status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Retrieves a payout order by its ID.
     */
    @GetMapping("/{payoutId}")
    public PayoutResponse getPayout(@PathVariable UUID payoutId) {
        log.info("GET /v1/payouts/{}", payoutId);
        return toPayoutResponse(payoutCommandHandler.getPayout(payoutId));
    }

    /**
     * Retrieves a payout order by its associated payment ID.
     */
    @GetMapping
    public PayoutResponse getPayoutByPaymentId(@RequestParam UUID paymentId) {
        log.info("GET /v1/payouts?paymentId={}", paymentId);
        return toPayoutResponse(payoutCommandHandler.getPayoutByPaymentId(paymentId));
    }

    private PayoutResponse toPayoutResponse(PayoutOrder order) {
        return new PayoutResponse(
                order.payoutId(),
                order.paymentId(),
                order.status().name(),
                order.payoutType().name(),
                order.fiatAmount(),
                order.targetCurrency(),
                order.paymentRail().name(),
                order.offRampPartner().partnerName(),
                order.partnerReference(),
                order.partnerSettledAt(),
                order.createdAt(),
                order.partnerSettledAt());
    }

    private BankAccount buildBankAccount(PayoutRequest request) {
        if (request.bankAccountNumber() == null || request.bankAccountNumber().isBlank()) {
            return null;
        }
        return new BankAccount(
                request.bankAccountNumber(),
                request.bankCode(),
                AccountType.valueOf(request.bankAccountType()),
                request.bankAccountCountry());
    }

    private MobileMoneyAccount buildMobileMoneyAccount(PayoutRequest request) {
        if (request.mobileMoneyProvider() == null || request.mobileMoneyProvider().isBlank()) {
            return null;
        }
        return new MobileMoneyAccount(
                MobileMoneyProvider.valueOf(request.mobileMoneyProvider()),
                request.mobileMoneyPhoneNumber(),
                request.mobileMoneyCountry());
    }
}
