package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.DEFAULT_TOLERANCE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReconciliationController IT")
class ReconciliationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @Autowired
    private ReconciliationLegRepository reconciliationLegRepository;

    @Nested
    @DisplayName("GET /v1/reconciliation/{paymentId}")
    class GetReconciliation {

        @Test
        @DisplayName("should return 200 with reconciliation and legs")
        void shouldReturn200WithReconciliationAndLegs() throws Exception {
            var paymentId = UUID.randomUUID();
            var record = ReconciliationRecord.create(paymentId, DEFAULT_TOLERANCE);
            var savedRecord = reconciliationRepository.save(record);

            var fiatInLeg = aLeg(savedRecord.recId(), ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD");
            var mintedLeg = aLeg(savedRecord.recId(), ReconciliationLegType.STABLECOIN_MINTED,
                    new BigDecimal("10000.000000"), "USDC");
            var redeemedLeg = aLeg(savedRecord.recId(), ReconciliationLegType.STABLECOIN_REDEEMED,
                    new BigDecimal("10000.000000"), "USDC");
            var fiatOutLeg = aLeg(savedRecord.recId(), ReconciliationLegType.FIAT_OUT,
                    new BigDecimal("9200.00"), "EUR");
            var fxRateLeg = aLeg(savedRecord.recId(), ReconciliationLegType.FX_RATE,
                    new BigDecimal("0.9200"), "EUR");

            reconciliationLegRepository.save(fiatInLeg);
            reconciliationLegRepository.save(mintedLeg);
            reconciliationLegRepository.save(redeemedLeg);
            reconciliationLegRepository.save(fiatOutLeg);
            reconciliationLegRepository.save(fxRateLeg);

            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recId", is(savedRecord.recId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("PENDING")))
                    .andExpect(jsonPath("$.legs", hasSize(5)))
                    .andExpect(jsonPath("$.appliedFxRate", notNullValue()))
                    .andExpect(jsonPath("$.expectedFiatOut", notNullValue()))
                    .andExpect(jsonPath("$.discrepancy", notNullValue()))
                    .andExpect(jsonPath("$.reconciledAt", nullValue()));
        }

        @Test
        @DisplayName("should return 200 with partial reconciliation (only fiat-in leg)")
        void shouldReturn200WithPartialReconciliation() throws Exception {
            var paymentId = UUID.randomUUID();
            var record = ReconciliationRecord.create(paymentId, DEFAULT_TOLERANCE);
            var savedRecord = reconciliationRepository.save(record);

            var fiatInLeg = aLeg(savedRecord.recId(), ReconciliationLegType.FIAT_IN,
                    new BigDecimal("10000.00"), "USD");
            reconciliationLegRepository.save(fiatInLeg);

            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("PENDING")))
                    .andExpect(jsonPath("$.legs", hasSize(1)))
                    .andExpect(jsonPath("$.legs[0].legType", is("FIAT_IN")))
                    .andExpect(jsonPath("$.appliedFxRate", nullValue()))
                    .andExpect(jsonPath("$.expectedFiatOut", nullValue()))
                    .andExpect(jsonPath("$.discrepancy", nullValue()));
        }

        @Test
        @DisplayName("should return 404 when reconciliation not found")
        void shouldReturn404WhenNotFound() throws Exception {
            var paymentId = UUID.randomUUID();

            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("LD-1003")));
        }

        @Test
        @DisplayName("should return 400 for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            mockMvc.perform(get("/v1/reconciliation/{paymentId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    private ReconciliationLeg aLeg(UUID recId, ReconciliationLegType legType,
                                    BigDecimal amount, String currency) {
        return new ReconciliationLeg(
                UUID.randomUUID(), recId, legType, amount, currency,
                UUID.randomUUID(), Instant.now()
        );
    }
}
