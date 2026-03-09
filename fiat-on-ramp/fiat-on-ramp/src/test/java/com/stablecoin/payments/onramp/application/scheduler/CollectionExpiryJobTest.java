package com.stablecoin.payments.onramp.application.scheduler;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.service.CollectionCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.stablecoin.payments.onramp.fixtures.ReconciliationFixtures.anExpiredAwaitingConfirmationOrder;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionExpiryJob")
class CollectionExpiryJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-09T12:00:00Z");

    @Mock private CollectionOrderRepository collectionOrderRepository;
    @Mock private CollectionCommandHandler collectionCommandHandler;

    private CollectionExpiryJob job;

    @BeforeEach
    void setUp() {
        var fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        job = new CollectionExpiryJob(collectionOrderRepository, collectionCommandHandler, fixedClock);
    }

    @Test
    @DisplayName("should delegate expired orders to CollectionCommandHandler")
    void shouldDelegateExpiredOrdersToCommandHandler() {
        // given
        var order = anExpiredAwaitingConfirmationOrder();

        given(collectionOrderRepository.findExpiredByStatus(CollectionStatus.AWAITING_CONFIRMATION, FIXED_NOW))
                .willReturn(List.of(order));

        // when
        job.expireCollections();

        // then
        then(collectionCommandHandler).should().expireCollection(order, FIXED_NOW);
    }

    @Test
    @DisplayName("should handle empty batch without calling command handler")
    void shouldHandleEmptyBatch() {
        // given
        given(collectionOrderRepository.findExpiredByStatus(CollectionStatus.AWAITING_CONFIRMATION, FIXED_NOW))
                .willReturn(List.of());

        // when
        job.expireCollections();

        // then
        then(collectionCommandHandler).shouldHaveNoInteractions();
    }
}
