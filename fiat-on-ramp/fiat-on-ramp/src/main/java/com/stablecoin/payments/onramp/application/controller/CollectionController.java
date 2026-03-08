package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.api.CollectionRequest;
import com.stablecoin.payments.onramp.api.CollectionResponse;
import com.stablecoin.payments.onramp.api.RefundRequest;
import com.stablecoin.payments.onramp.api.RefundResponse;
import com.stablecoin.payments.onramp.domain.model.AccountType;
import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;
import com.stablecoin.payments.onramp.domain.model.PaymentRailType;
import com.stablecoin.payments.onramp.domain.model.PspIdentifier;
import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.domain.service.CollectionCommandHandler;
import com.stablecoin.payments.onramp.domain.service.RefundCommandHandler;
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
 * REST controller for collection order lifecycle management.
 * <p>
 * Thin HTTP handler that delegates all business logic to
 * {@link CollectionCommandHandler} and {@link RefundCommandHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/v1/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionCommandHandler collectionCommandHandler;
    private final RefundCommandHandler refundCommandHandler;

    /**
     * Initiates a new collection order.
     * <p>
     * Idempotent: returns 200 OK with the existing order if the same paymentId
     * is submitted again. Returns 201 CREATED on first creation.
     */
    @PostMapping
    public ResponseEntity<CollectionResponse> initiateCollection(
            @Valid @RequestBody CollectionRequest request) {
        log.info("POST /v1/collections paymentId={}", request.paymentId());

        var amount = new Money(request.amount(), request.currency());
        var paymentRail = new PaymentRail(
                PaymentRailType.valueOf(request.paymentRailType()),
                request.railCountry(),
                request.railCurrency());
        var psp = new PspIdentifier(request.pspId(), request.pspName());
        var senderAccount = new BankAccount(
                request.senderAccountHash(),
                request.senderBankCode(),
                AccountType.valueOf(request.senderAccountType()),
                request.senderAccountCountry());

        var result = collectionCommandHandler.initiateCollection(
                request.paymentId(),
                request.correlationId(),
                amount,
                paymentRail,
                psp,
                senderAccount);

        var response = toCollectionResponse(result.order());
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Retrieves a collection order by its ID.
     */
    @GetMapping("/{collectionId}")
    public CollectionResponse getCollection(@PathVariable UUID collectionId) {
        log.info("GET /v1/collections/{}", collectionId);
        return toCollectionResponse(collectionCommandHandler.getCollection(collectionId));
    }

    /**
     * Retrieves a collection order by its associated payment ID.
     */
    @GetMapping
    public CollectionResponse getCollectionByPaymentId(@RequestParam UUID paymentId) {
        log.info("GET /v1/collections?paymentId={}", paymentId);
        return toCollectionResponse(collectionCommandHandler.getCollectionByPaymentId(paymentId));
    }

    /**
     * Initiates a refund for a collected order.
     */
    @PostMapping("/{collectionId}/refunds")
    public ResponseEntity<RefundResponse> initiateRefund(
            @PathVariable UUID collectionId,
            @Valid @RequestBody RefundRequest request) {
        log.info("POST /v1/collections/{}/refunds", collectionId);

        var refundAmount = new Money(request.refundAmount(), request.currency());
        var refund = refundCommandHandler.initiateRefund(collectionId, refundAmount, request.reason());

        return ResponseEntity.status(HttpStatus.CREATED).body(toRefundResponse(refund));
    }

    private CollectionResponse toCollectionResponse(CollectionOrder order) {
        return new CollectionResponse(
                order.collectionId(),
                order.paymentId(),
                order.status().name(),
                order.paymentRail().rail().name(),
                order.psp().pspName(),
                order.pspReference(),
                order.pspSettledAt(),
                order.createdAt(),
                order.expiresAt());
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(
                refund.refundId(),
                refund.collectionId(),
                refund.status().name(),
                refund.refundAmount().amount(),
                refund.refundAmount().currency(),
                refund.initiatedAt(),
                refund.completedAt());
    }
}
