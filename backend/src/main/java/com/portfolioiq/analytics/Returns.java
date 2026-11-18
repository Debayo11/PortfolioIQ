package com.portfolioiq.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Time-weighted return. Each day's return is (end value - net flow + dividends) / previous end value - 1, so adding
 * or withdrawing money does not look like performance. Daily returns are chained into an index that starts at 100.
 * Days with no holdings the day before are skipped (the index stays flat). Flows are treated as end-of-day, and a
 * single day's loss is limited to -100%.
 */
public final class Returns {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100).setScale(8);
    private static final BigDecimal NEG_ONE = BigDecimal.valueOf(-1);

    private Returns() {}

    /** index has one entry per point; dailyReturns has the same size with null where a return is undefined. */
    public record Result(List<BigDecimal> index, List<BigDecimal> dailyReturns, BigDecimal twrPercent) {}

    public static Result timeWeighted(List<DayPoint> pts) {
        if (pts.isEmpty()) return new Result(List.of(), List.of(), BigDecimal.ZERO);
        List<BigDecimal> index = new ArrayList<>();
        List<BigDecimal> returns = new ArrayList<>();
        BigDecimal idx = HUNDRED;
        index.add(idx);
        returns.add(null);
        for (int i = 1; i < pts.size(); i++) {
            BigDecimal prev = pts.get(i - 1).marketValue();
            DayPoint cur = pts.get(i);
            if (prev.signum() > 0) {
                BigDecimal r = cur.marketValue().subtract(cur.netFlow()).add(cur.dividends())
                        .divide(prev, 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
                if (r.compareTo(NEG_ONE) < 0) r = NEG_ONE;
                idx = idx.multiply(BigDecimal.ONE.add(r)).setScale(8, RoundingMode.HALF_UP);
                returns.add(r);
            } else {
                returns.add(null);
            }
            index.add(idx);
        }
        return new Result(index, returns, idx.subtract(HUNDRED).setScale(4, RoundingMode.HALF_UP));
    }

    /** A plain price series rebased to 100 at the first date, or null if there is no price at or before it. */
    public static List<BigDecimal> priceIndex(PriceSeries series, List<LocalDate> dates) {
        if (dates.isEmpty()) return null;
        var base = series.onOrBefore(dates.get(0));
        if (base.isEmpty() || base.get().signum() <= 0) return null;
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal last = HUNDRED;
        for (LocalDate d : dates) {
            var p = series.onOrBefore(d);
            if (p.isPresent()) last = p.get().multiply(BigDecimal.valueOf(100)).divide(base.get(), 8, RoundingMode.HALF_UP);
            out.add(last);
        }
        return out;
    }
}
