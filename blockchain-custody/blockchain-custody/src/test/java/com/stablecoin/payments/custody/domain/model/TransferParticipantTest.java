package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.custody.domain.model.ParticipantType.FEE;
import static com.stablecoin.payments.custody.domain.model.ParticipantType.INPUT;
import static com.stablecoin.payments.custody.domain.model.ParticipantType.OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransferParticipant")
class TransferParticipantTest {

    private static final UUID TRANSFER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID WALLET_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String ADDRESS = "0xParticipantAddress";
    private static final BigDecimal AMOUNT = new BigDecimal("250.00");
    private static final String ASSET_CODE = "USDC";

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethod {

        @Test
        @DisplayName("creates participant with correct fields")
        void createsParticipant() {
            var result = TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, AMOUNT, ASSET_CODE
            );

            assertThat(result.participantId()).isNotNull();
            assertThat(result.transferId()).isEqualTo(TRANSFER_ID);
            assertThat(result.participantType()).isEqualTo(INPUT);
            assertThat(result.address()).isEqualTo(ADDRESS);
            assertThat(result.walletId()).isEqualTo(WALLET_ID);
            assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
            assertThat(result.assetCode()).isEqualTo(ASSET_CODE);
        }

        @Test
        @DisplayName("creates OUTPUT participant")
        void createsOutputParticipant() {
            var result = TransferParticipant.create(
                    TRANSFER_ID, OUTPUT, ADDRESS, WALLET_ID, AMOUNT, ASSET_CODE
            );

            assertThat(result.participantType()).isEqualTo(OUTPUT);
        }

        @Test
        @DisplayName("creates FEE participant")
        void createsFeeParticipant() {
            var result = TransferParticipant.create(
                    TRANSFER_ID, FEE, ADDRESS, null, new BigDecimal("0.50"), "ETH"
            );

            assertThat(result.participantType()).isEqualTo(FEE);
            assertThat(result.walletId()).isNull();
            assertThat(result.assetCode()).isEqualTo("ETH");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects null transferId")
        void rejectsNullTransferId() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    null, INPUT, ADDRESS, WALLET_ID, AMOUNT, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("transferId is required");
        }

        @Test
        @DisplayName("rejects null participantType")
        void rejectsNullParticipantType() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, null, ADDRESS, WALLET_ID, AMOUNT, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("participantType is required");
        }

        @Test
        @DisplayName("rejects null address")
        void rejectsNullAddress() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, null, WALLET_ID, AMOUNT, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("address is required");
        }

        @Test
        @DisplayName("rejects blank address")
        void rejectsBlankAddress() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, "  ", WALLET_ID, AMOUNT, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("address is required");
        }

        @Test
        @DisplayName("rejects null amount")
        void rejectsNullAmount() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, null, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects zero amount")
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, BigDecimal.ZERO, ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects negative amount")
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, new BigDecimal("-5.00"), ASSET_CODE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects null assetCode")
        void rejectsNullAssetCode() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, AMOUNT, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assetCode is required");
        }

        @Test
        @DisplayName("rejects blank assetCode")
        void rejectsBlankAssetCode() {
            assertThatThrownBy(() -> TransferParticipant.create(
                    TRANSFER_ID, INPUT, ADDRESS, WALLET_ID, AMOUNT, "  "
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assetCode is required");
        }

        @Test
        @DisplayName("allows null walletId")
        void allowsNullWalletId() {
            var result = TransferParticipant.create(
                    TRANSFER_ID, FEE, ADDRESS, null, AMOUNT, ASSET_CODE
            );

            assertThat(result.walletId()).isNull();
        }
    }
}
