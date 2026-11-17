package com.portfolioiq.market;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedMarketDataProviderTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:34:56Z"), ZoneOffset.UTC);
    private final SimulatedMarketDataProvider provider = new SimulatedMarketDataProvider(clock);

    @Test void quotesAreLabelledSimulated() {
        Quote q = provider.latest("acme").orElseThrow();
        assertTrue(q.simulated());
        assertEquals("SIMULATED", q.source());
        assertEquals("ACME", q.symbol());
    }

    @Test void quotesAreDeterministic() {
        assertEquals(provider.latest("ACME").orElseThrow(), provider.latest("ACME").orElseThrow());
        assertNotEquals(provider.latest("ACME").orElseThrow().price(), provider.latest("ZZZZ").orElseThrow().price());
    }

    @Test void pricesAreAlwaysPositive() {
        for (String s : new String[]{"A", "AAPL", "MSFT", "X.Y", "VERYLONGSYMBOL"}) {
            Quote q = provider.latest(s).orElseThrow();
            assertTrue(q.price().signum() > 0 && q.previousClose().signum() > 0);
            provider.history(s, 365).forEach(p -> assertTrue(p.price().signum() > 0));
        }
    }

    @Test void historyHasExpectedShape() {
        assertEquals(24, provider.history("ACME", 1).size());
        assertEquals(30, provider.history("ACME", 30).size());
    }
}
