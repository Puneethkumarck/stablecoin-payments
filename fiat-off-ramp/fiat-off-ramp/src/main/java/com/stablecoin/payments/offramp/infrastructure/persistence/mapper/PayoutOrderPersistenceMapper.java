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
                .version(order.version())
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

        BankAccount bankAccount = mapBankAccount(entity);
        MobileMoneyAccount mobileMoneyAccount = mapMobileMoneyAccount(entity);

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
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static BankAccount mapBankAccount(PayoutOrderEntity entity) {
        boolean hasAccountNumber = entity.getBankAccountNumber() != null;
        boolean hasCode = entity.getBankCode() != null;
        boolean hasType = entity.getBankAccountType() != null;
        boolean hasCountry = entity.getBankCountry() != null;

        boolean hasAny = hasAccountNumber || hasCode || hasType || hasCountry;
        boolean hasAll = hasAccountNumber && hasCode && hasType && hasCountry;

        if (!hasAny) {
            return null;
        }
        if (!hasAll) {
            throw new IllegalStateException(
                    "Corrupted payout_orders row: partial bank account columns populated — "
                            + "accountNumber=%s, bankCode=%s, accountType=%s, country=%s".formatted(
                            hasAccountNumber, hasCode, hasType, hasCountry));
        }
        return new BankAccount(
                entity.getBankAccountNumber(),
                entity.getBankCode(),
                AccountType.valueOf(entity.getBankAccountType()),
                entity.getBankCountry()
        );
    }

    private static MobileMoneyAccount mapMobileMoneyAccount(PayoutOrderEntity entity) {
        boolean hasProvider = entity.getMobileMoneyProvider() != null;
        boolean hasPhone = entity.getMobileMoneyPhone() != null;
        boolean hasCountry = entity.getMobileMoneyCountry() != null;

        boolean hasAny = hasProvider || hasPhone || hasCountry;
        boolean hasAll = hasProvider && hasPhone && hasCountry;

        if (!hasAny) {
            return null;
        }
        if (!hasAll) {
            throw new IllegalStateException(
                    "Corrupted payout_orders row: partial mobile money columns populated — "
                            + "provider=%s, phone=%s, country=%s".formatted(
                            hasProvider, hasPhone, hasCountry));
        }
        return new MobileMoneyAccount(
                MobileMoneyProvider.valueOf(entity.getMobileMoneyProvider()),
                entity.getMobileMoneyPhone(),
                entity.getMobileMoneyCountry()
        );
    }
}
