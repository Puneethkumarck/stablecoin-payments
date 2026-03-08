package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.ChainTransferEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface ChainTransferEntityUpdater {

    default void updateEntity(@MappingTarget ChainTransferEntity entity, ChainTransfer transfer) {
        if (transfer == null) {
            return;
        }
        entity.setPaymentId(transfer.paymentId());
        entity.setCorrelationId(transfer.correlationId());
        entity.setTransferType(transfer.transferType());
        entity.setParentTransferId(transfer.parentTransferId());
        entity.setChainId(transfer.chainId() != null ? transfer.chainId().value() : null);
        entity.setStablecoin(transfer.stablecoin() != null ? transfer.stablecoin().ticker() : null);
        entity.setAmount(transfer.amount());
        entity.setFromWalletId(transfer.fromWalletId());
        entity.setToAddress(transfer.toWalletAddress());
        entity.setFromAddress(transfer.fromAddress());
        entity.setNonce(transfer.nonce());
        entity.setTxHash(transfer.txHash());
        entity.setStatus(transfer.status());
        entity.setBlockNumber(transfer.blockNumber());
        entity.setBlockConfirmedAt(transfer.blockConfirmedAt());
        entity.setConfirmations(transfer.confirmations());
        entity.setGasUsed(transfer.gasUsed());
        entity.setGasPriceGwei(transfer.gasPriceGwei());
        entity.setAttemptCount(transfer.attemptCount());
        entity.setFailureReason(transfer.failureReason());
        entity.setErrorCode(transfer.errorCode());
        entity.setUpdatedAt(transfer.updatedAt());
    }
}
