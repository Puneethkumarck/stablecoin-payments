package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.api.ReconciliationResponse;
import com.stablecoin.payments.ledger.api.ReconciliationResponse.LegResponse;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final LedgerQueryHandler queryHandler;

    @GetMapping("/{paymentId}")
    public ReconciliationResponse getReconciliation(@PathVariable UUID paymentId) {
        log.info("GET /v1/reconciliation/{}", paymentId);
        var record = queryHandler.getReconciliation(paymentId);
        return toReconciliationResponse(record);
    }

    private ReconciliationResponse toReconciliationResponse(ReconciliationRecord record) {
        var legs = record.legs().stream()
                .map(leg -> new LegResponse(
                        leg.legType().name(),
                        leg.amount(),
                        leg.currency(),
                        leg.receivedAt()))
                .toList();

        var appliedFxRate = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.FX_RATE)
                .map(l -> l.amount())
                .findFirst()
                .orElse(null);

        var expectedFiatOut = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.FIAT_OUT)
                .map(l -> l.amount())
                .findFirst()
                .orElse(null);

        var discrepancy = calculateDiscrepancy(record);

        return new ReconciliationResponse(
                record.recId(),
                record.paymentId(),
                record.status().name(),
                legs,
                appliedFxRate,
                expectedFiatOut,
                discrepancy,
                record.reconciledAt()
        );
    }

    private BigDecimal calculateDiscrepancy(ReconciliationRecord record) {
        var mintedAmount = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.STABLECOIN_MINTED)
                .map(l -> l.amount())
                .findFirst()
                .orElse(null);
        var redeemedAmount = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.STABLECOIN_REDEEMED)
                .map(l -> l.amount())
                .findFirst()
                .orElse(null);
        if (mintedAmount == null || redeemedAmount == null) {
            return null;
        }
        return mintedAmount.subtract(redeemedAmount).abs();
    }
}
