package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.AbstractIntegrationTest;
import com.stablecoin.payments.fx.domain.model.FxQuote;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.fx.application.config.IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER;
import static com.stablecoin.payments.fx.fixtures.CorridorRateFixtures.aUsdEurRate;
import static com.stablecoin.payments.fx.fixtures.FxQuoteFixtures.anActiveQuote;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("FxQuoteController IT")
class FxQuoteControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FxQuoteRepository quoteRepository;

    @Autowired
    private LiquidityPoolRepository poolRepository;

    @Autowired
    private RateCache rateCache;

    @Nested
    @DisplayName("GET /v1/fx/quote")
    class GetQuote {

        @BeforeEach
        void seedRate() {
            rateCache.put("USD", "EUR", aUsdEurRate());
        }

        @Test
        @DisplayName("should return 200 OK with quote response")
        void shouldReturn200WithQuoteResponse() throws Exception {
            mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR")
                            .param("amount", "10000.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteId", notNullValue()))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")))
                    .andExpect(jsonPath("$.rate", notNullValue()))
                    .andExpect(jsonPath("$.provider", is("REFINITIV")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing fromCurrency")
        void shouldReturn400ForMissingFromCurrency() throws Exception {
            mockMvc.perform(get("/v1/fx/quote")
                            .param("toCurrency", "EUR")
                            .param("amount", "10000.00"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing amount")
        void shouldReturn400ForMissingAmount() throws Exception {
            mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "USD")
                            .param("toCurrency", "EUR"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 503 Service Unavailable when rate is not available")
        void shouldReturn503WhenRateUnavailable() throws Exception {
            mockMvc.perform(get("/v1/fx/quote")
                            .param("fromCurrency", "JPY")
                            .param("toCurrency", "BRL")
                            .param("amount", "10000.00"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code", is("FX-4001")));
        }
    }

    @Nested
    @DisplayName("GET /v1/fx/quote/{quoteId}")
    class GetQuoteById {

        @Test
        @DisplayName("should return 200 OK for existing quote")
        void shouldReturn200ForExistingQuote() throws Exception {
            var quote = anActiveQuote();
            var saved = quoteRepository.save(quote);

            mockMvc.perform(get("/v1/fx/quote/{quoteId}", saved.quoteId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteId", is(saved.quoteId().toString())))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing quote")
        void shouldReturn404ForNonExistingQuote() throws Exception {
            var quoteId = UUID.randomUUID();

            mockMvc.perform(get("/v1/fx/quote/{quoteId}", quoteId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("FX-1001")));
        }
    }

    @Nested
    @DisplayName("POST /v1/fx/lock/{quoteId}")
    class LockRate {

        @Test
        @DisplayName("should return 201 Created with lock response")
        void shouldReturn201WithLockResponse() throws Exception {
            var quote = anActiveQuote();
            var saved = quoteRepository.save(quote);
            poolRepository.save(aUsdEurPool());

            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(paymentId, correlationId);

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", saved.quoteId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lockId", notNullValue()))
                    .andExpect(jsonPath("$.quoteId", is(saved.quoteId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.fromCurrency", is("USD")))
                    .andExpect(jsonPath("$.toCurrency", is("EUR")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing quote")
        void shouldReturn404ForNonExistingQuote() throws Exception {
            var quoteId = UUID.randomUUID();
            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("FX-1001")));
        }

        @Test
        @DisplayName("should return 410 Gone for expired quote")
        void shouldReturn410ForExpiredQuote() throws Exception {
            var expiredQuote = new FxQuote(
                    UUID.randomUUID(), "USD", "EUR",
                    new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                    new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                    0, 30, new BigDecimal("30.00000000"), "REFINITIV", "REF-123",
                    FxQuoteStatus.ACTIVE, Instant.now().minusSeconds(600), Instant.now().minusSeconds(10)
            );
            var saved = quoteRepository.save(expiredQuote);
            poolRepository.save(aUsdEurPool());

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", saved.quoteId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code", is("FX-1002")));
        }

        @Test
        @DisplayName("should return 409 Conflict for already locked quote")
        void shouldReturn409ForAlreadyLockedQuote() throws Exception {
            var lockedQuote = new FxQuote(
                    UUID.randomUUID(), "USD", "EUR",
                    new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                    new BigDecimal("0.9200000000"), new BigDecimal("1.0869565217"),
                    0, 30, new BigDecimal("30.00000000"), "REFINITIV", "REF-123",
                    FxQuoteStatus.LOCKED, Instant.now(), Instant.now().plusSeconds(300)
            );
            var saved = quoteRepository.save(lockedQuote);

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", saved.quoteId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("FX-1003")));
        }

        @Test
        @DisplayName("should return 422 Unprocessable Entity for insufficient liquidity")
        void shouldReturn422ForInsufficientLiquidity() throws Exception {
            var quote = new FxQuote(
                    UUID.randomUUID(), "GBP", "EUR",
                    new BigDecimal("10000000.00000000"), new BigDecimal("11600000.00000000"),
                    new BigDecimal("1.1600000000"), new BigDecimal("0.8620689655"),
                    0, 25, new BigDecimal("25000.00000000"), "REFINITIV", "REF-456",
                    FxQuoteStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(300)
            );
            var saved = quoteRepository.save(quote);

            // Save a pool with very low balance for GBP/EUR
            var lowPool = new LiquidityPool(
                    UUID.randomUUID(), "GBP", "EUR",
                    new BigDecimal("100.00000000"), BigDecimal.ZERO,
                    new BigDecimal("50000.00000000"), new BigDecimal("2000000.00000000"),
                    Instant.now()
            );
            poolRepository.save(lowPool);

            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "GB",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", saved.quoteId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code", is("FX-3001")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            var quoteId = UUID.randomUUID();
            var requestBody = """
                    {
                        "sourceCountry": "US"
                    }
                    """;

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("FX-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400ForMissingIdempotencyKey() throws Exception {
            var quoteId = UUID.randomUUID();
            var requestBody = """
                    {
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "sourceCountry": "US",
                        "targetCountry": "DE"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/v1/fx/lock/{quoteId}", quoteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("FX-0001")))
                    .andExpect(jsonPath("$.message", is("Idempotency-Key header is required for mutating requests")));
        }
    }
}
