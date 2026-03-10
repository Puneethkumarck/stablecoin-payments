package com.stablecoin.payments.offramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.offramp.domain.model.AccountType;
import com.stablecoin.payments.offramp.domain.model.BankAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyProvider;
import com.stablecoin.payments.offramp.domain.model.PartnerIdentifier;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.StablecoinTicker;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.PayoutOrderEntity;
import org.mapstruct.Mapper;

@Mapper
public interface PayoutOrderPersistenceMapper {

    default PayoutOrderEntity toEntity(PayoutOrder order) {
        if (order == null) {
            return null;
        }
        return PayoutOrderEntity.builder()
                .payoutId(order.payoutId())
                .paymentId(order.paymentId())
                .correlationId(order.correlationId())
                .transferId(order.transferId())
                .payoutType(order.payoutType())
                .stablecoin(order.stablecoin() != null ? order.stablecoin().ticker() : null)
                .redeemedAmount(order.redeemedAmount())
                .targetCurrency(order.targetCurrency())
                .fiatAmount(order.fiatAmount())
                .appliedFxRate(order.appliedFxRate())
                .recipientId(order.recipientId())
                .recipientAccountHash(order.recipientAccountHash())
                .paymentRail(order.paymentRail())
                .offRampPartnerId(order.offRampPartner() != null ? order.offRampPartner().partnerId() : null)
                .offRampPartnerName(order.offRampPartner() != null ? order.offRampPartner().partnerName() : null)
                .status(order.status())
                .partnerReference(order.partnerReference())
                .partnerSettledAt(order.partnerSettledAt())
                .failureReason(order.failureReason())
                .errorCode(order.errorCode())
                .bankAccountNumber(order.bankAccount() != null ? order.bankAccount().accountNumber() : null)
                .bankCode(order.bankAccount() != null ? order.bankAccount().bankCode() : null)
                .bankAccountType(order.bankAccount() != null ? order.bankAccount().accountType().name() : null)
                .bankCountry(order.bankAccount() != null ? order.bankAccount().country() : null)
                .mobileMoneyProvider(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().provider().name() : null)
                .mobileMoneyPhone(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().phoneNumber() : null)
                .mobileMoneyCountry(order.mobileMoneyAccount() != null ? order.mobileMoneyAccount().country() : null)
                .createdAt(order.createdAt())
                .updatedAt(order.updatedAt())
                .build();
    }

    default PayoutOrder toDomain(PayoutOrderEntity entity) {
        if (entity == null) {
            return null;
        }

        StablecoinTicker stablecoin = entity.getStablecoin() != null
                ? StablecoinTicker.of(entity.getStablecoin())
                : null;

        PartnerIdentifier offRampPartner = null;
        if (entity.getOffRampPartnerId() != null && entity.getOffRampPartnerName() != null) {
            offRampPartner = new PartnerIdentifier(entity.getOffRampPartnerId(), entity.getOffRampPartnerName());
        }

        BankAccount bankAccount = null;
        if (entity.getBankAccountNumber() != null && entity.getBankCode() != null
                && entity.getBankAccountType() != null && entity.getBankCountry() != null) {
            bankAccount = new BankAccount(
                    entity.getBankAccountNumber(),
                    entity.getBankCode(),
                    AccountType.valueOf(entity.getBankAccountType()),
                    entity.getBankCountry()
            );
        }

        MobileMoneyAccount mobileMoneyAccount = null;
        if (entity.getMobileMoneyProvider() != null && entity.getMobileMoneyPhone() != null
                && entity.getMobileMoneyCountry() != null) {
            mobileMoneyAccount = new MobileMoneyAccount(
                    MobileMoneyProvider.valueOf(entity.getMobileMoneyProvider()),
                    entity.getMobileMoneyPhone(),
                    entity.getMobileMoneyCountry()
            );
        }

        return new PayoutOrder(
                entity.getPayoutId(),
                entity.getPaymentId(),
                entity.getCorrelationId(),
                entity.getTransferId(),
                entity.getPayoutType(),
                stablecoin,
                entity.getRedeemedAmount(),
                entity.getTargetCurrency(),
                entity.getFiatAmount(),
                entity.getAppliedFxRate(),
                entity.getRecipientId(),
                entity.getRecipientAccountHash(),
                bankAccount,
                mobileMoneyAccount,
                entity.getPaymentRail(),
                offRampPartner,
                entity.getStatus(),
                entity.getPartnerReference(),
                entity.getPartnerSettledAt(),
                entity.getFailureReason(),
                entity.getErrorCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
