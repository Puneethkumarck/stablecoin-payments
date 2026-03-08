package com.stablecoin.payments.fx;

import com.stablecoin.payments.fx.domain.model.FxQuoteStatus;
import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.port.RateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.fx.application.config.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the FX quote/lock lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * fallback rate provider (fixed rates: USD:EUR 0.92, spread 30 bps, fee 30 bps),
 * and fallback in-memory rate cache.
 */
@DisplayName("FX Quote & Lock Lifecycle — Business Tests")
class FxQuoteLockLifecycleTest extends AbstractBusinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FxQuoteRepository quoteRepository;

    @Autowired
    private LiquidityPoolRepository poolRepository;

    @Autowired
    private RateCache rateCache;

    @BeforeEach
    void seedData() {
        // Seed the rate cache so quote creation has a rate available
        rateCache.put("USD", "EUR", aUsdEurRate());
        // Seed a liquidity pool for USD/EUR corridor
        poolRepository.save(aUsdEurPool());
    }

    // ── Scenario 1: Happy Path — Quote → Lock ────────────────────────

    @Nested
    @DisplayName("Happy Path — Quote → Lock")
    class HappyPath {

        @Test
        @DisplayName("should create quote with correct rate/spread/fee, then lock it reserving pool liquidity")
        void shouldCreateQuoteThenLockSuccessfully() throws Exception {
            // Step 1: GET /v1/fx/quote — create a quote
            var quoteResult = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "10000.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteId", notNullValue()))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")))
                    .andExpect(jsonPath("$.sourceAmount", notNullValue()))
                    .andExpect(jsonPath("$.targetAmount", notNullValue()))
                    .andExpect(jsonPath("$.rate", notNullValue()))
                    .andExpect(jsonPath("$.feeBps", is(30)))
                    .andExpect(jsonPath("$.feeAmount", notNullValue()))
                    .andExpect(jsonPath("$.provider", is("REFINITIV")))
                    .andExpect(jsonPath("$.expiresAt", notNullValue()))
                    .andReturn();

            var quoteId = extractField(quoteResult, "quoteId");

            // Step 2: GET /v1/fx/quote/{quoteId} — verify persisted quote
            mockMvc.perform(get("/v1/fx/quote/{quoteId}", quoteId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteId", is(quoteId)))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")));

            // Step 3: POST /v1/fx/lock/{quoteId} — lock the rate
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentId, correlationId);

            var lockResult = mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lockId", notNullValue()))
                    .andExpect(jsonPath("$.quoteId", is(quoteId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")))
                    .andExpect(jsonPath("$.lockedRate", notNullValue()))
                    .andExpect(jsonPath("$.feeBps", is(30)))
                    .andExpect(jsonPath("$.lockedAt", notNullValue()))
                    .andExpect(jsonPath("$.expiresAt", notNullValue()))
                    .andReturn();

            // Step 4: Verify quote status is now LOCKED
            var lockedQuote = quoteRepository.findById(UUID.fromString(quoteId))
                    .orElseThrow();
            assertThat(lockedQuote.status()).isEqualTo(FxQuoteStatus.LOCKED);

            // Step 5: Verify pool liquidity was reserved
            var pool = poolRepository.findByCorridor("USD", "EUR").orElseThrow();
            assertThat(pool.reservedBalance()).usingComparator(BigDecimal::compareTo)
                    .isGreaterThan(BigDecimal.ZERO);

            // Step 6: Verify outbox event was created
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fx_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);
        }
    }

    // ── Scenario 2: Quote Expiry ─────────────────────────────────────

    @Nested
    @DisplayName("Quote Expiry — lock after TTL returns 410 Gone")
    class QuoteExpiry {

        @Test
        @DisplayName("should return 410 Gone when attempting to lock an expired quote")
        void shouldReturn410GoneForExpiredQuote() throws Exception {
            // Insert a quote that is already past its TTL via direct DB save
            var expiredQuote = new com.stablecoin.payments.fx.domain.model.FxQuote(
                    UUID.randomUUID(), "USD", "EUR",
                    new BigDecimal("10000.00000000"), new BigDecimal("9172.40000000"),
                    new BigDecimal("0.9172400000"), new BigDecimal("1.0902255639"),
                    30, 30, new BigDecimal("30.00000000"), "fallback", null,
                    FxQuoteStatus.ACTIVE,
                    Instant.now().minusSeconds(600),
                    Instant.now().minusSeconds(10)
            );
            var savedQuote = quoteRepository.save(expiredQuote);

            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", savedQuote.quoteId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code", is("FX-1002")));
        }
    }

    // ── Scenario 3: Lock Idempotency ─────────────────────────────────

    @Nested
    @DisplayName("Lock Idempotency — same quote locked twice returns 409 Conflict")
    class LockIdempotency {

        @Test
        @DisplayName("should return 409 Conflict when locking an already-locked quote")
        void shouldReturn409ConflictForAlreadyLockedQuote() throws Exception {
            // Step 1: Create a quote via API
            var quoteResult = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "5000.00"))
                    .andExpect(status().isOk())
                    .andReturn();

            var quoteId = extractField(quoteResult, "quoteId");

            // Step 2: Lock the quote with paymentId-A
            var paymentIdA = UUID.randomUUID();
            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentIdA, UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isCreated());

            // Step 3: Attempt to lock the same quote again with paymentId-B — should be 409
            var paymentIdB = UUID.randomUUID();
            var secondLockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentIdB, UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(secondLockRequestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("FX-1003")));
        }
    }

    // ── Scenario 3b: Payment-Level Idempotency ─────────────────────────

    @Nested
    @DisplayName("Payment Idempotency — same paymentId returns 200 OK")
    class PaymentIdempotency {

        @Test
        @DisplayName("should return 200 OK with existing lock when same paymentId is submitted again")
        void shouldReturn200OkForSamePaymentId() throws Exception {
            // Step 1: Create a quote
            var quoteResult = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "8000.00"))
                    .andExpect(status().isOk())
                    .andReturn();

            var quoteId = extractField(quoteResult, "quoteId");
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentId, correlationId);

            // Step 2: First lock — 201 Created
            var firstResult = mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andReturn();

            var lockId = extractField(firstResult, "lockId");

            // Step 3: Second lock with same paymentId — 200 OK, same lockId
            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lockId", is(lockId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())));
        }
    }

    // ── Scenario 4: Insufficient Liquidity ───────────────────────────

    @Nested
    @DisplayName("Insufficient Liquidity — lock fails with 422")
    class InsufficientLiquidity {

        @Test
        @DisplayName("should return 422 Unprocessable Entity when pool balance is too low")
        void shouldReturn422WhenPoolBalanceTooLow() throws Exception {
            // Create a separate corridor with very low balance (GBP/EUR)
            // First seed the rate cache for GBP/EUR
            rateCache.put("GBP", "EUR",
                    com.stablecoin.payments.fx.domain.model.CorridorRate.builder()
                            .fromCurrency("GBP")
                            .toCurrency("EUR")
                            .rate(new BigDecimal("1.1600000000"))
                            .spreadBps(25)
                            .feeBps(25)
                            .provider("fallback")
                            .ageMs(0)
                            .build());

            // Save pool with tiny balance
            var tinyPool = new LiquidityPool(
                    UUID.randomUUID(), "GBP", "EUR",
                    new BigDecimal("10.00000000"), BigDecimal.ZERO,
                    new BigDecimal("50000.00000000"), new BigDecimal("2000000.00000000"),
                    Instant.now()
            );
            poolRepository.save(tinyPool);

            // Create a large quote for GBP/EUR
            var quoteResult = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "GBP")
                            .param("toCurrency", "EUR")
                            .param("amount", "100000.00"))
                    .andExpect(status().isOk())
                    .andReturn();

            var quoteId = extractField(quoteResult, "quoteId");

            // Attempt to lock — should fail with insufficient liquidity
            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "GB",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code", is("FX-3001")));

            // Verify pool balance was NOT affected
            var pool = poolRepository.findByCorridor("GBP", "EUR").orElseThrow();
            assertThat(pool.reservedBalance()).usingComparator(BigDecimal::compareTo)
                    .isEqualTo(BigDecimal.ZERO);
        }
    }

    // ── Scenario 5: Corridor List ────────────────────────────────────

    @Nested
    @DisplayName("Corridor List — GET /v1/fx/corridors")
    class CorridorList {

        @Test
        @DisplayName("should return supported corridors with rate information")
        void shouldReturnSupportedCorridors() throws Exception {
            mockMvc.perform(get("/v1/fx/corridors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].fromCurrency", is("USD")))
                    .andExpect(jsonPath("$[0].toCurrency", is("EUR")))
                    .andExpect(jsonPath("$[0].indicativeRate", notNullValue()))
                    .andExpect(jsonPath("$[0].feeBps", is(30)))
                    .andExpect(jsonPath("$[0].spreadBps", is(30)))
                    .andExpect(jsonPath("$[0].provider", is("REFINITIV")));
        }
    }

    // ── Scenario 6: Pool Liquidity Tracking ──────────────────────────

    @Nested
    @DisplayName("Pool Liquidity — balance decreases after lock")
    class PoolLiquidityTracking {

        @Test
        @DisplayName("should reduce available balance and increase reserved balance after lock")
        void shouldTrackLiquidityAfterLock() throws Exception {
            // Capture initial pool state
            var initialPool = poolRepository.findByCorridor("USD", "EUR").orElseThrow();
            var initialAvailable = initialPool.availableBalance();

            // Create a quote
            var quoteResult = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "25000.00"))
                    .andExpect(status().isOk())
                    .andReturn();

            var quoteId = extractField(quoteResult, "quoteId");
            var targetAmountStr = extractField(quoteResult, "targetAmount");
            var expectedReserved = new BigDecimal(targetAmountStr);

            // Lock the quote
            var paymentId = UUID.randomUUID();
            var lockRequestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentId, UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequestBody))
                    .andExpect(status().isCreated());

            // Verify pool balances changed correctly
            var updatedPool = poolRepository.findByCorridor("USD", "EUR").orElseThrow();
            var expectedAvailable = initialAvailable.subtract(expectedReserved);

            assertThat(updatedPool.availableBalance())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(expectedAvailable);
            assertThat(updatedPool.reservedBalance())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(expectedReserved);
        }
    }

    // ── Scenario 7: Multiple Locks Cumulative ────────────────────────

    @Nested
    @DisplayName("Multiple Locks — cumulative pool reservation")
    class MultipleLocks {

        @Test
        @DisplayName("should accumulate reserved balance across multiple locks")
        void shouldAccumulateReservedBalance() throws Exception {
            // Create and lock first quote
            var quoteResult1 = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "5000.00"))
                    .andExpect(status().isOk())
                    .andReturn();
            var quoteId1 = extractField(quoteResult1, "quoteId");

            var lockRequest1 = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequest1))
                    .andExpect(status().isCreated());

            var poolAfterFirst = poolRepository.findByCorridor("USD", "EUR").orElseThrow();
            var reservedAfterFirst = poolAfterFirst.reservedBalance();

            // Create and lock second quote
            var quoteResult2 = mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "3000.00"))
                    .andExpect(status().isOk())
                    .andReturn();
            var quoteId2 = extractField(quoteResult2, "quoteId");

            var lockRequest2 = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(lockRequest2))
                    .andExpect(status().isCreated());

            // Verify cumulative reservation
            var poolAfterSecond = poolRepository.findByCorridor("USD", "EUR").orElseThrow();
            assertThat(poolAfterSecond.reservedBalance())
                    .usingComparator(BigDecimal::compareTo)
                    .isGreaterThan(reservedAfterFirst);

            // Verify two outbox events were created (one per lock)
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fx_outbox_record",
                    Integer.class);
            assertThat(outboxCount).isGreaterThanOrEqualTo(2);
        }
    }

    // ── Scenario 8: Rate Unavailable for Unknown Corridor ────────────

    @Nested
    @DisplayName("Rate Unavailable — unsupported corridor returns 503")
    class RateUnavailable {

        @Test
        @DisplayName("should return 503 Service Unavailable for unsupported corridor")
        void shouldReturn503ForUnsupportedCorridor() throws Exception {
            mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "JPY")
                            .param("toCurrency", "BRL")
                            .param("amount", "10000.00"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code", is("FX-4001")));
        }
    }

    // ── Helper ───────────────────────────────────────────────────────

    private static String extractField(MvcResult result, String fieldName) throws Exception {
        var json = result.getResponse().getContentAsString();
        var pattern = "\"" + fieldName + "\":";
        var start = json.indexOf(pattern) + pattern.length();

        // Handle string values (wrapped in quotes)
        if (json.charAt(start) == '"') {
            var valueStart = start + 1;
            var valueEnd = json.indexOf("\"", valueStart);
            return json.substring(valueStart, valueEnd);
        }

        // Handle numeric values (not wrapped in quotes)
        var valueEnd = start;
        while (valueEnd < json.length()
                && json.charAt(valueEnd) != ','
                && json.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return json.substring(start, valueEnd);
    }
}
