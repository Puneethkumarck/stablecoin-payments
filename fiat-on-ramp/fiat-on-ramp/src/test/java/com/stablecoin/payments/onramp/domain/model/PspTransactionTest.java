package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.onramp.domain.model.PspTransactionDirection.DEBIT;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aMoney;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.COLLECTION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PspTransaction")
class PspTransactionTest {

    private static final String PSP_NAME = "Stripe";
    private static final String PSP_REFERENCE = "pi_abc123";
    private static final String EVENT_TYPE = "payment_intent.succeeded";
    private static final String STATUS = "succeeded";
    private static final String RAW_RESPONSE = "{\"id\": \"pi_abc123\"}";

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethod {

        @Test
        @DisplayName("creates transaction with all fields populated")
        void createsTransactionWithAllFields() {
            var txn = PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE);

            var expected = PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE);

            assertThat(txn)
                    .usingRecursiveComparison()
                    .ignoringFields("pspTxnId", "receivedAt")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates unique pspTxnId")
        void generatesUniquePspTxnId() {
            var txn = PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE);

            assertThat(txn.pspTxnId()).isNotNull();
        }

        @Test
        @DisplayName("sets receivedAt timestamp")
        void setsReceivedAt() {
            var txn = PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE);

            assertThat(txn.receivedAt()).isNotNull();
        }

        @Test
        @DisplayName("allows null rawResponse")
        void allowsNullRawResponse() {
            var txn = PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, null);

            assertThat(txn.rawResponse()).isNull();
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("rejects null collectionId")
        void rejectsNullCollectionId() {
            assertThatThrownBy(() -> PspTransaction.create(
                    null, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("collectionId is required");
        }

        @Test
        @DisplayName("rejects null pspName")
        void rejectsNullPspName() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, null, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pspName is required");
        }

        @Test
        @DisplayName("rejects blank pspName")
        void rejectsBlankPspName() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, "   ", PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pspName is required");
        }

        @Test
        @DisplayName("rejects null pspReference")
        void rejectsNullPspReference() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, null, DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pspReference is required");
        }

        @Test
        @DisplayName("rejects blank pspReference")
        void rejectsBlankPspReference() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, "   ", DEBIT,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pspReference is required");
        }

        @Test
        @DisplayName("rejects null direction")
        void rejectsNullDirection() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, null,
                    EVENT_TYPE, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("direction is required");
        }

        @Test
        @DisplayName("rejects null eventType")
        void rejectsNullEventType() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    null, aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("eventType is required");
        }

        @Test
        @DisplayName("rejects blank eventType")
        void rejectsBlankEventType() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    "   ", aMoney(), STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("eventType is required");
        }

        @Test
        @DisplayName("rejects null amount")
        void rejectsNullAmount() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, null, STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount is required");
        }

        @Test
        @DisplayName("rejects null status")
        void rejectsNullStatus() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), null, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("status is required");
        }

        @Test
        @DisplayName("rejects blank status")
        void rejectsBlankStatus() {
            assertThatThrownBy(() -> PspTransaction.create(
                    COLLECTION_ID, PSP_NAME, PSP_REFERENCE, DEBIT,
                    EVENT_TYPE, aMoney(), "   ", RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("status is required");
        }
    }
}
