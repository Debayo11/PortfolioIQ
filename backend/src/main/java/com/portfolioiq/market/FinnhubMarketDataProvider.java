package com.portfolioiq.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Real quotes from Finnhub's /quote endpoint (fields: c current, pc previous close, t unix time).
 * The API key is sent as a header so it never appears in URLs or logs. Finnhub's free tier allows about
 * 60 calls per minute, so the refresh job caps requests per cycle. Volume and market cap are not part of
 * this endpoint and are left empty rather than invented. Historical candles are a paid feature, so history
 * is built from the prices this app stores over time.
 */
public final class FinnhubMarketDataProvider implements MarketDataProvider {
    public static final String SOURCE = "FINNHUB";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FinnhubQuote(Double c, Double pc, Long t) {}

    private final RestClient client;
    private final Clock clock;

    public FinnhubMarketDataProvider(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override public String name() { return SOURCE; }
    @Override public boolean simulated() { return false; }
    @Override public boolean supportsHistory() { return false; }

    @Override
    public Optional<Quote> latest(String symbol) {
        FinnhubQuote r = client.get()
                .uri(u -> u.path("/quote").queryParam("symbol", symbol.trim().toUpperCase(Locale.ROOT)).build())
                .retrieve()
                .body(FinnhubQuote.class);
        // Unknown symbols come back as zeros.
        if (r == null || r.c() == null || r.c() <= 0 || r.t() == null || r.t() <= 0) return Optional.empty();
        BigDecimal prev = r.pc() != null && r.pc() > 0 ? BigDecimal.valueOf(r.pc()) : null;
        Instant asOf = Instant.ofEpochSecond(r.t());
        if (asOf.isAfter(clock.instant())) asOf = clock.instant();
        return Optional.of(new Quote(symbol.trim().toUpperCase(Locale.ROOT), BigDecimal.valueOf(r.c()), prev, null, null,
                asOf, SOURCE, false));
    }

    @Override
    public List<PricePoint> history(String symbol, int days) { return List.of(); }
}
