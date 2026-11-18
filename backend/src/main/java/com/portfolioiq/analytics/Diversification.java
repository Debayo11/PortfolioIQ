package com.portfolioiq.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Concentration measures. The Herfindahl-Hirschman Index (sum of squared weights) is 1.0 for one holding and
 * 1/N for N equal holdings; its inverse is the "effective number" of holdings.
 */
public final class Diversification {
    private Diversification() {}

    public record Weight(String label, BigDecimal value) {}

    public record Result(int holdings, BigDecimal hhi, BigDecimal effectiveHoldings, BigDecimal topWeightPercent,
                         BigDecimal top3WeightPercent, String level, String explanation) {}

    public static Result analyse(List<Weight> weights) {
        List<BigDecimal> values = weights.stream().map(Weight::value).filter(v -> v != null && v.signum() > 0)
                .sorted(Comparator.reverseOrder()).toList();
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (values.isEmpty() || total.signum() == 0) {
            return new Result(0, null, null, null, null, "NONE", "There are no open holdings to analyse.");
        }
        BigDecimal hhi = BigDecimal.ZERO, top3 = BigDecimal.ZERO;
        BigDecimal top = values.get(0).divide(total, 10, RoundingMode.HALF_UP);
        for (int i = 0; i < values.size(); i++) {
            BigDecimal w = values.get(i).divide(total, 10, RoundingMode.HALF_UP);
            hhi = hhi.add(w.multiply(w));
            if (i < 3) top3 = top3.add(w);
        }
        BigDecimal effective = BigDecimal.ONE.divide(hhi, 2, RoundingMode.HALF_UP);
        String level = effective.compareTo(BigDecimal.valueOf(3)) < 0 || top.compareTo(new BigDecimal("0.5")) > 0
                ? "CONCENTRATED"
                : effective.compareTo(BigDecimal.valueOf(6)) < 0 || top.compareTo(new BigDecimal("0.3")) > 0
                ? "MODERATE" : "DIVERSIFIED";
        String explanation = switch (level) {
            case "CONCENTRATED" -> "Your value is concentrated in very few holdings, so one position can dominate results.";
            case "MODERATE" -> "Your holdings are spread across several positions, but a few still carry much of the weight.";
            default -> "Your value is spread fairly evenly across many holdings.";
        };
        return new Result(values.size(), hhi.setScale(4, RoundingMode.HALF_UP), effective,
                pct(top), pct(top3), level, explanation);
    }

    private static BigDecimal pct(BigDecimal w) { return w.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP); }
}
