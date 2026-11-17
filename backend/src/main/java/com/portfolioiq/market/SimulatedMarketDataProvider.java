package com.portfolioiq.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * DEVELOPMENT ONLY. Generates deterministic, clearly labelled synthetic prices so the app can be demonstrated
 * without an API key. Every quote carries source "SIMULATED" and simulated=true.
 *
 * The same symbol and time always give the same price. The generator uses floating point for the synthetic curve
 * only; it is converted to BigDecimal immediately and nothing in the accounting code uses floating point.
 */
public final class SimulatedMarketDataProvider implements MarketDataProvider {
    public static final String SOURCE = "SIMULATED";
    private static final double TWO_PI = Math.PI * 2;

    private final Clock clock;

    public SimulatedMarketDataProvider(Clock clock) { this.clock = clock; }

    @Override public String name() { return SOURCE; }
    @Override public boolean simulated() { return true; }
    @Override public boolean supportsHistory() { return true; }

    @Override
    public Optional<Quote> latest(String rawSymbol) {
        String s = normalise(rawSymbol);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        long epochDay = now.getEpochSecond() / 86_400;
        long minuteOfDay = (now.getEpochSecond() % 86_400) / 60;

        BigDecimal price = money(intraday(s, epochDay, minuteOfDay));
        BigDecimal previousClose = money(close(s, epochDay - 1));
        long volume = 100_000L + Math.floorMod(mix(s.hashCode(), epochDay * 7 + 3), 9_900_000L);
        BigDecimal shares = BigDecimal.valueOf(50_000_000L + Math.floorMod(mix(s.hashCode(), 99), 4_950_000_000L));
        BigDecimal marketCap = price.multiply(shares).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new Quote(s, price, previousClose, volume, marketCap, now, SOURCE, true));
    }

    @Override
    public List<PricePoint> history(String rawSymbol, int days) {
        String s = normalise(rawSymbol);
        Instant now = clock.instant();
        List<PricePoint> points = new ArrayList<>();
        if (days <= 1) {
            Instant hour = now.truncatedTo(ChronoUnit.HOURS);
            for (int i = 23; i >= 0; i--) {
                Instant t = hour.minus(i, ChronoUnit.HOURS);
                long day = t.getEpochSecond() / 86_400;
                long minute = (t.getEpochSecond() % 86_400) / 60;
                points.add(new PricePoint(t, money(intraday(s, day, minute))));
            }
            return points;
        }
        long today = now.getEpochSecond() / 86_400;
        for (long d = today - days + 1; d <= today; d++) {
            points.add(new PricePoint(Instant.ofEpochSecond(d * 86_400), money(close(s, d))));
        }
        return points;
    }

    // ---- synthetic curve -------------------------------------------------------------------

    private static double base(String s) { return 20 + Math.floorMod(mix(s.hashCode(), 1), 48_000L) / 100.0; }

    private static double dayLevel(String s, long epochDay) {
        double phase1 = unit(s, 11) * TWO_PI;
        double phase2 = unit(s, 12) * TWO_PI;
        return 1 + 0.25 * Math.sin(epochDay / 37.0 + phase1)
                 + 0.08 * Math.sin(epochDay / 7.3 + phase2)
                 + 0.02 * noise(s, epochDay);
    }

    private static double close(String s, long epochDay) { return base(s) * dayLevel(s, epochDay); }

    private static double intraday(String s, long epochDay, long minuteOfDay) {
        double phase = unit(s, 13) * TWO_PI;
        return close(s, epochDay) * (1 + 0.012 * Math.sin(TWO_PI * minuteOfDay / 1440.0 + phase));
    }

    private static BigDecimal money(double v) { return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP); }

    private static String normalise(String symbol) { return symbol.trim().toUpperCase(Locale.ROOT); }

    private static double unit(String s, long k) { return (noise(s, k) + 1) / 2; }

    /** Deterministic value in [-1, 1). */
    private static double noise(String s, long k) {
        long x = mix(s.hashCode(), k);
        return ((x >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /** splitmix64-style integer mixing: stable across JVMs and runs. */
    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 30; x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27; x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return x;
    }
}
