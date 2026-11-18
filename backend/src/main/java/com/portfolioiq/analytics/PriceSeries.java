package com.portfolioiq.analytics;

import com.portfolioiq.market.PricePoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** One price per UTC day for an asset, with "last known price on or before a date" lookup. */
public final class PriceSeries {
    private final NavigableMap<LocalDate, BigDecimal> byDay = new TreeMap<>();

    public static PriceSeries of(List<PricePoint> points) {
        PriceSeries s = new PriceSeries();
        points.stream().sorted(Comparator.comparing(PricePoint::time))
                .forEach(p -> s.byDay.put(p.time().atZone(ZoneOffset.UTC).toLocalDate(), p.price()));
        return s;
    }

    public void put(LocalDate day, BigDecimal price) { byDay.put(day, price); }

    public Optional<BigDecimal> onOrBefore(LocalDate day) {
        var e = byDay.floorEntry(day);
        return e == null ? Optional.empty() : Optional.of(e.getValue());
    }

    public boolean isEmpty() { return byDay.isEmpty(); }
}
