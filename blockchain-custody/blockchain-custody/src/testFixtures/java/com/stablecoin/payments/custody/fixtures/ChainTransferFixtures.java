package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.TransferType;

import java.math.BigDecimal;
import java.util.UUID;

public final class ChainTransferFixtures {

    private ChainTransferFixtures() {}

    public static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID CORRELATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID FROM_WALLET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID PARENT_TRANSFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final String TO_ADDRESS = "0xRecipientAddress1234567890abcdef";
    public static final String FROM_ADDRESS = "0xSenderAddress1234567890abcdef";
    public static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    public static final String TX_HASH_RESUBMIT = "0xresubmit234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    public static final ChainId CHAIN_BASE = new ChainId("base");
    public static final StablecoinTicker USDC = StablecoinTicker.of("USDC");
    public static final BigDecimal AMOUNT = new BigDecimal("1000.00");

    /**
     * A fresh PENDING transfer (FORWARD type, no parent).
     */
    public static ChainTransfer aPendingTransfer() {
        return ChainTransfer.initiate(
                PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
        );
    }

    /**
     * A PENDING RETURN transfer with parentTransferId.
     */
    public static ChainTransfer aPendingReturnTransfer() {
        return ChainTransfer.initiate(
                PAYMENT_ID, CORRELATION_ID, TransferType.RETURN, PARENT_TRANSFER_ID,
                USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
        );
    }

    /**
     * A transfer in CHAIN_SELECTED state.
     */
    public static ChainTransfer aChainSelectedTransfer() {
        return aPendingTransfer().selectChain(CHAIN_BASE);
    }

    /**
     * A transfer in SIGNING state.
     */
    public static ChainTransfer aSigningTransfer() {
        return aChainSelectedTransfer().startSigning(42L);
    }

    /**
     * A transfer in SUBMITTED state (attemptCount = 1).
     */
    public static ChainTransfer aSubmittedTransfer() {
        return aSigningTransfer().submit(TX_HASH);
    }

    /**
     * A transfer in CONFIRMING state.
     */
    public static ChainTransfer aConfirmingTransfer() {
        return aSubmittedTransfer().startConfirming();
    }

    /**
     * A transfer in CONFIRMED (terminal) state.
     */
    public static ChainTransfer aConfirmedTransfer() {
        return aConfirmingTransfer().confirm(
                12345L, 15, new BigDecimal("0.002100"), new BigDecimal("25.5")
        );
    }

    /**
     * A transfer in RESUBMITTING state.
     */
    public static ChainTransfer aResubmittingTransfer() {
        return aSubmittedTransfer().markForResubmission();
    }

    /**
     * A transfer in FAILED (terminal) state.
     */
    public static ChainTransfer aFailedTransfer() {
        return aChainSelectedTransfer().fail("Insufficient gas", "GAS_LIMIT_EXCEEDED");
    }
}
