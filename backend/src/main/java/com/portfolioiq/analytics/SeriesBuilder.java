package com.portfolioiq.analytics;

import com.portfolioiq.finance.Position;
import com.portfolioiq.finance.PositionCalculator;
import com.portfolioiq.finance.TradeEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Rebuilds a portfolio's day-by-day holdings value and cost basis from its ledger and historical prices.
 * Cost basis comes from PositionCalculator, so it always agrees with the rest of the application.
 * When no market price exists for a day, the last trade price is used and the day is flagged approximated.
 */
public final class SeriesBuilder {
    private SeriesBuilder() {}

    private record Priced(BigDecimal price, boolean approximated) {}

    /** One point per day from 'from' to 'to' inclusive. Events before 'from' still shape the starting state. */
    public static List<DayPoint> build(List<AssetEvent> events, Map<Long, PriceSeries> prices, LocalDate from, LocalDate to) {
        List<AssetEvent> sorted = events.stream().sorted(Comparator.comparing(e -> e.event().executedAt())).toList();
        Map<LocalDate, List<AssetEvent>> byDate = new HashMap<>();
        for (AssetEvent e : sorted) byDate.computeIfAbsent(e.date(), k -> new ArrayList<>()).add(e);

        LocalDate start = sorted.isEmpty() || from.isBefore(sorted.get(0).date()) ? from : sorted.get(0).date();
        Map<Long, List<TradeEvent>> applied = new HashMap<>();
        Map<Long, Position> positions = new HashMap<>();
        Map<Long, BigDecimal> lastTrade = new HashMap<>();
        List<DayPoint> out = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(to); d = d.plusDays(1)) {
            List<AssetEvent> today = byDate.getOrDefault(d, List.of());
            Set<Long> touched = new LinkedHashSet<>();
            for (AssetEvent ae : today) {
                applied.computeIfAbsent(ae.assetId(), k -> new ArrayList<>()).add(ae.event());
                touched.add(ae.assetId());
                if (ae.event().type() != TradeEvent.Type.DIVIDEND && ae.event().unitPrice().signum() > 0) {
                    lastTrade.put(ae.assetId(), ae.event().unitPrice());
                }
            }
            for (Long id : touched) positions.put(id, PositionCalculator.calculate(applied.get(id)));

            boolean approximated = false;
            BigDecimal flow = BigDecimal.ZERO, dividends = BigDecimal.ZERO;
            for (AssetEvent ae : today) {
                TradeEvent e = ae.event();
                switch (e.type()) {
                    case BUY -> flow = flow.add(e.quantity().multiply(e.unitPrice()).add(e.fee()));
                    case SELL -> flow = flow.subtract(e.quantity().multiply(e.unitPrice()).subtract(e.fee()));
                    case DIVIDEND -> dividends = dividends.add(e.quantity().multiply(e.unitPrice()).subtract(e.fee()));
                    case TRANSFER_IN, TRANSFER_OUT -> {
                        Priced p = priceFor(ae.assetId(), d, prices, lastTrade);
                        approximated |= p.approximated();
                        BigDecimal v = e.quantity().multiply(p.price());
                        flow = e.type() == TradeEvent.Type.TRANSFER_IN ? flow.add(v) : flow.subtract(v);
                    }
                }
            }

            BigDecimal value = BigDecimal.ZERO, cost = BigDecimal.ZERO;
            for (Map.Entry<Long, Position> en : positions.entrySet()) {
                Position pos = en.getValue();
                cost = cost.add(pos.totalCost());
                if (pos.quantity().signum() <= 0) continue;
                Priced p = priceFor(en.getKey(), d, prices, lastTrade);
                approximated |= p.approximated();
                value = value.add(pos.quantity().multiply(p.price()));
            }
            if (!d.isBefore(from)) {
                out.add(new DayPoint(d, money(value), money(cost), money(flow), money(dividends), approximated));
            }
        }
        return out;
    }

    /** Adds several portfolios' series day by day. All inputs must cover the same dates. */
    public static List<DayPoint> sum(List<List<DayPoint>> all) {
        if (all.isEmpty()) return List.of();
        List<DayPoint> out = new ArrayList<>();
        for (int i = 0; i < all.get(0).size(); i++) {
            BigDecimal v = BigDecimal.ZERO, c = BigDecimal.ZERO, f = BigDecimal.ZERO, dv = BigDecimal.ZERO;
            boolean approx = false;
            for (List<DayPoint> series : all) {
                DayPoint p = series.get(i);
                v = v.add(p.marketValue());
                c = c.add(p.costBasis());
                f = f.add(p.netFlow());
                dv = dv.add(p.dividends());
                approx |= p.approximated();
            }
            out.add(new DayPoint(all.get(0).get(i).date(), money(v), money(c), money(f), money(dv), approx));
        }
        return out;
    }

    private static Priced priceFor(long assetId, LocalDate d, Map<Long, PriceSeries> prices, Map<Long, BigDecimal> lastTrade) {
        PriceSeries s = prices.get(assetId);
        if (s != null) {
            Optional<BigDecimal> p = s.onOrBefore(d);
            if (p.isPresent()) return new Priced(p.get(), false);
        }
        BigDecimal t = lastTrade.get(assetId);
        return t != null ? new Priced(t, true) : new Priced(BigDecimal.ZERO, true);
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(4, RoundingMode.HALF_UP); }
}
