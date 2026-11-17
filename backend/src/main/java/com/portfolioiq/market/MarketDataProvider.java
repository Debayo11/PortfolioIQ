package com.portfolioiq.market;

import java.util.List;
import java.util.Optional;

public interface MarketDataProvider {
    /** Human-readable provider name, e.g. "FINNHUB" or "SIMULATED". */
    String name();

    boolean simulated();

    /** Latest quote, or empty if the symbol is unknown. Implementations throw on transport failures. */
    Optional<Quote> latest(String symbol);

    /** Whether history() can be answered directly. Otherwise history is built from stored prices. */
    boolean supportsHistory();

    /** days <= 1 returns hourly points for the last 24 hours, otherwise one point per day. */
    List<PricePoint> history(String symbol, int days);
}
