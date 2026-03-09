package com.stablecoin.payments.offramp.domain.model;

import java.util.Map;

public record StablecoinTicker(String ticker, String issuer, int decimals) {

    private static final Map<String, StablecoinInfo> SUPPORTED = Map.of(
            "USDC", new StablecoinInfo("circle", 6),
            "USDT", new StablecoinInfo("tether", 6),
            "EURC", new StablecoinInfo("circle_euro", 6),
            "PYUSD", new StablecoinInfo("paypal", 6),
            "RLUSD", new StablecoinInfo("ripple", 6)
    );

    private record StablecoinInfo(String issuer, int decimals) {}

    public StablecoinTicker {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        var info = SUPPORTED.get(ticker);
        if (info == null) {
            throw new IllegalArgumentException(
                    "Unsupported stablecoin: %s. Must be one of: %s".formatted(ticker, SUPPORTED.keySet()));
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = info.issuer();
        }
        if (decimals <= 0) {
            decimals = info.decimals();
        }
    }

    /**
     * Factory that auto-fills issuer and decimals from the ticker.
     */
    public static StablecoinTicker of(String ticker) {
        return new StablecoinTicker(ticker, null, 0);
    }
}
