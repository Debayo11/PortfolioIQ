package com.portfolioiq.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Average-cost position accounting.
 * <ul>
 *   <li>BUY adds quantity and cost (quantity x price + fee).</li>
 *   <li>SELL removes cost at the current average cost; realized P&amp;L = proceeds - fee - cost removed.</li>
 *   <li>DIVIDEND adds (shares x dividend per share - fee) to income and does not change cost.</li>
 * </ul>
 * Internal math uses scale 8; outputs are rounded to 4 (HALF_UP). No floating point is used anywhere.
 */
public final class PositionCalculator {
    public static final int MONEY_SCALE = 4;
    public static final int INTERNAL_SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PositionCalculator() {}

    public static Position calculate(List<TradeEvent> events) {
        List<TradeEvent> ordered = events.stream()
                .sorted(Comparator.comparing(TradeEvent::executedAt)
                        .thenComparingInt(e -> e.type() == TradeEvent.Type.BUY ? 0 : 1)) // buys settle first on ties
                .toList();

        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;

        for (TradeEvent e : ordered) {
            fees = fees.add(e.fee());
            switch (e.type()) {
                case BUY -> {
                    qty = qty.add(e.quantity());
                    cost = cost.add(e.quantity().multiply(e.unitPrice())).add(e.fee());
                }
                case SELL -> {
                    if (e.quantity().compareTo(qty) > 0) {
                        throw new InsufficientHoldingsException(
                                "Cannot sell " + e.quantity().stripTrailingZeros().toPlainString()
                                        + " units; only " + qty.stripTrailingZeros().toPlainString() + " held");
                    }
                    BigDecimal avg = cost.divide(qty, INTERNAL_SCALE, ROUNDING);
                    BigDecimal costRemoved = e.quantity().equals(qty) ? cost : avg.multiply(e.quantity());
                    BigDecimal proceeds = e.quantity().multiply(e.unitPrice()).subtract(e.fee());
                    realized = realized.add(proceeds.subtract(costRemoved));
                    qty = qty.subtract(e.quantity());
                    cost = qty.signum() == 0 ? BigDecimal.ZERO : cost.subtract(costRemoved);
                }
                case DIVIDEND -> dividends = dividends.add(
                        e.quantity().multiply(e.unitPrice()).subtract(e.fee()));
            }
        }

        BigDecimal avgCost = qty.signum() == 0 ? BigDecimal.ZERO : cost.divide(qty, MONEY_SCALE, ROUNDING);
        return new Position(qty.setScale(INTERNAL_SCALE, ROUNDING), cost.setScale(MONEY_SCALE, ROUNDING), avgCost,
                realized.setScale(MONEY_SCALE, ROUNDING), dividends.setScale(MONEY_SCALE, ROUNDING),
                fees.setScale(MONEY_SCALE, ROUNDING));
    }
}
