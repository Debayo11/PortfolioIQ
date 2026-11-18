package com.portfolioiq.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Historical risk statistics. Statistics on returns (not money) use double arithmetic for the square root;
 * the results are converted to BigDecimal immediately. Volatility is annualised with 252 trading days.
 */
public final class RiskMetrics {
    private static final int TRADING_DAYS = 252;

    private RiskMetrics() {}

    /** All values are percentages. Fields are null when there is not enough history to compute them. */
    public record Risk(BigDecimal annualizedVolatilityPercent, BigDecimal maxDrawdownPercent, LocalDate drawdownPeakDate,
                       LocalDate drawdownTroughDate, BigDecimal bestDayPercent, LocalDate bestDay,
                       BigDecimal worstDayPercent, LocalDate worstDay, BigDecimal positiveDaysPercent,
                       int returnObservations) {}

    public static Risk compute(List<DayPoint> pts, Returns.Result r) {
        List<Double> values = new ArrayList<>();
        int bestIdx = -1, worstIdx = -1, positive = 0;
        for (int i = 0; i < r.dailyReturns().size(); i++) {
            BigDecimal x = r.dailyReturns().get(i);
            if (x == null) continue;
            values.add(x.doubleValue());
            if (bestIdx < 0 || x.compareTo(r.dailyReturns().get(bestIdx)) > 0) bestIdx = i;
            if (worstIdx < 0 || x.compareTo(r.dailyReturns().get(worstIdx)) < 0) worstIdx = i;
            if (x.signum() > 0) positive++;
        }
        int n = values.size();
        if (n == 0) return new Risk(null, null, null, null, null, null, null, null, null, 0);

        BigDecimal vol = null;
        if (n >= 2) {
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
            vol = BigDecimal.valueOf(Math.sqrt(ss / (n - 1)) * Math.sqrt(TRADING_DAYS) * 100).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal peak = r.index().get(0), worst = BigDecimal.ZERO;
        LocalDate peakDate = pts.get(0).date(), worstPeak = peakDate, trough = peakDate;
        for (int i = 0; i < r.index().size(); i++) {
            BigDecimal v = r.index().get(i);
            if (v.compareTo(peak) > 0) { peak = v; peakDate = pts.get(i).date(); }
            BigDecimal dd = v.divide(peak, 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            if (dd.compareTo(worst) < 0) { worst = dd; worstPeak = peakDate; trough = pts.get(i).date(); }
        }
        BigDecimal mdd = worst.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return new Risk(vol, mdd, worst.signum() == 0 ? null : worstPeak, worst.signum() == 0 ? null : trough,
                pct(r.dailyReturns().get(bestIdx)), pts.get(bestIdx).date(),
                pct(r.dailyReturns().get(worstIdx)), pts.get(worstIdx).date(),
                BigDecimal.valueOf(positive * 100.0 / n).setScale(1, RoundingMode.HALF_UP), n);
    }

    private static BigDecimal pct(BigDecimal r) { return r.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP); }
}
