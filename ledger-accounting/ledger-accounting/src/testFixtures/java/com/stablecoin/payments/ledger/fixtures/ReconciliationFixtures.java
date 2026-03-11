package com.stablecoin.payments.ledger.fixtures;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;

public final class ReconciliationFixtures {

    public static final UUID REC_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    public static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01");

    private ReconciliationFixtures() {
    }

    public static ReconciliationRecord aPendingRecord() {
        return new ReconciliationRecord(
                REC_ID,
                PAYMENT_ID,
                ReconciliationStatus.PENDING,
                DEFAULT_TOLERANCE,
                null,
                List.of(),
                NOW,
                NOW,
                0L
        );
    }

    public static ReconciliationLeg aLeg(ReconciliationLegType legType, BigDecimal amount, String currency) {
        return new ReconciliationLeg(
                UUID.randomUUID(),
                REC_ID,
                legType,
                amount,
                currency,
                UUID.randomUUID(),
                NOW
        );
    }

    public static ReconciliationLeg aFiatInLeg() {
        return aLeg(ReconciliationLegType.FIAT_IN, new BigDecimal("10000.00"), "USD");
    }

    public static ReconciliationLeg aStablecoinMintedLeg() {
        return aLeg(ReconciliationLegType.STABLECOIN_MINTED, new BigDecimal("10000.000000"), "USDC");
    }

    public static ReconciliationLeg aChainTransferredLeg() {
        return aLeg(ReconciliationLegType.CHAIN_TRANSFERRED, new BigDecimal("10000.000000"), "USDC");
    }

    public static ReconciliationLeg aStablecoinRedeemedLeg() {
        return aLeg(ReconciliationLegType.STABLECOIN_REDEEMED, new BigDecimal("10000.000000"), "USDC");
    }

    public static ReconciliationLeg aFiatOutLeg() {
        return aLeg(ReconciliationLegType.FIAT_OUT, new BigDecimal("9200.00"), "EUR");
    }

    public static ReconciliationLeg aFxRateLeg() {
        return aLeg(ReconciliationLegType.FX_RATE, new BigDecimal("0.9200"), "EUR");
    }

    public static ReconciliationRecord aRecordWithAllRequiredLegs() {
        var record = aPendingRecord();
        record = record.addLeg(aFiatInLeg());
        record = record.addLeg(aStablecoinMintedLeg());
        record = record.addLeg(aChainTransferredLeg());
        record = record.addLeg(aStablecoinRedeemedLeg());
        record = record.addLeg(aFiatOutLeg());
        return record;
    }
}
