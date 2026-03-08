package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.ChainTransferEntity;
import org.mapstruct.Mapper;

@Mapper
public interface ChainTransferPersistenceMapper {

    default ChainTransferEntity toEntity(ChainTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        return ChainTransferEntity.builder()
                .transferId(transfer.transferId())
                .paymentId(transfer.paymentId())
                .correlationId(transfer.correlationId())
                .transferType(transfer.transferType())
                .parentTransferId(transfer.parentTransferId())
                .chainId(transfer.chainId() != null ? transfer.chainId().value() : null)
                .stablecoin(transfer.stablecoin() != null ? transfer.stablecoin().ticker() : null)
                .amount(transfer.amount())
                .fromWalletId(transfer.fromWalletId())
                .toAddress(transfer.toWalletAddress())
                .fromAddress(transfer.fromAddress())
                .nonce(transfer.nonce())
                .txHash(transfer.txHash())
                .status(transfer.status())
                .blockNumber(transfer.blockNumber())
                .blockConfirmedAt(transfer.blockConfirmedAt())
                .confirmations(transfer.confirmations())
                .gasUsed(transfer.gasUsed())
                .gasPriceGwei(transfer.gasPriceGwei())
                .attemptCount(transfer.attemptCount())
                .failureReason(transfer.failureReason())
                .errorCode(transfer.errorCode())
                .createdAt(transfer.createdAt())
                .updatedAt(transfer.updatedAt())
                .build();
    }

    default ChainTransfer toDomain(ChainTransferEntity entity) {
        if (entity == null) {
            return null;
        }

        ChainId chainId = null;
        if (entity.getChainId() != null) {
            chainId = new ChainId(entity.getChainId());
        }

        StablecoinTicker stablecoin = null;
        if (entity.getStablecoin() != null) {
            stablecoin = StablecoinTicker.of(entity.getStablecoin());
        }

        return new ChainTransfer(
                entity.getTransferId(),
                entity.getPaymentId(),
                entity.getCorrelationId(),
                entity.getTransferType(),
                entity.getParentTransferId(),
                chainId,
                stablecoin,
                entity.getAmount(),
                entity.getFromWalletId(),
                entity.getToAddress(),
                entity.getFromAddress(),
                entity.getNonce(),
                entity.getTxHash(),
                entity.getStatus(),
                entity.getBlockNumber(),
                entity.getBlockConfirmedAt(),
                entity.getConfirmations(),
                entity.getGasUsed(),
                entity.getGasPriceGwei(),
                entity.getAttemptCount(),
                entity.getFailureReason(),
                entity.getErrorCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
