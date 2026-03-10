package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.AbstractIntegrationTest;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.StablecoinRedemption;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import com.stablecoin.payments.offramp.domain.port.StablecoinRedemptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinTicker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StablecoinRedemptionPersistenceAdapter IT")
class StablecoinRedemptionPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private StablecoinRedemptionRepository adapter;

    @Autowired
    private PayoutOrderRepository payoutOrderAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve redemption by id")
    void shouldSaveAndRetrieveById() {
        var order = savePayoutOrder();
        var redemption = StablecoinRedemption.create(
                order.payoutId(), aStablecoinTicker(),
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                "EUR", "circle", "circle_ref_001");
        var saved = adapter.save(redemption);

        assertThat(adapter.findById(saved.redemptionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find redemption by payout id")
    void shouldFindByPayoutId() {
        var order = savePayoutOrder();
        var redemption = StablecoinRedemption.create(
                order.payoutId(), aStablecoinTicker(),
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                "EUR", "circle", "circle_ref_002");
        var saved = adapter.save(redemption);

        assertThat(adapter.findByPayoutId(order.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when payout id not found")
    void shouldReturnEmptyWhenPayoutIdNotFound() {
        assertThat(adapter.findByPayoutId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should persist all stablecoin ticker fields via round-trip")
    void shouldPersistStablecoinTickerRoundTrip() {
        var order = savePayoutOrder();
        var redemption = StablecoinRedemption.create(
                order.payoutId(), aStablecoinTicker(),
                new BigDecimal("500.00"), new BigDecimal("460.00"),
                "EUR", "circle", "circle_ref_003");
        var saved = adapter.save(redemption);

        assertThat(adapter.findById(saved.redemptionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── Constraint Validation ────────────────────────────────────────────

    @Test
    @DisplayName("should reject redemption with non-existent payout id (FK constraint)")
    void shouldRejectRedemptionWithNonExistentPayoutId() {
        var redemption = StablecoinRedemption.create(
                UUID.randomUUID(), aStablecoinTicker(),
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                "EUR", "circle", "circle_ref_orphan");

        assertThatThrownBy(() -> adapter.save(redemption))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PayoutOrder savePayoutOrder() {
        return payoutOrderAdapter.save(aPendingOrder());
    }
}
