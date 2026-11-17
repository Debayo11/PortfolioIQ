package com.portfolioiq.finance;

import java.math.BigDecimal;

import static com.portfolioiq.finance.PositionCalculator.MONEY_SCALE;
import static com.portfolioiq.finance.PositionCalculator.ROUNDING;

/** Result of replaying an asset's events. All money values are rounded to 4 decimal places. */
public record Position(BigDecimal quantity, BigDecimal totalCost, BigDecimal averageCost,
                       BigDecimal realizedPnl, BigDecimal dividendIncome, BigDecimal totalFees) {

    /** Cost per unit at 8 decimal places, used when carrying cost basis across a transfer. */
    public BigDecimal unitCost() {
        return quantity.signum() == 0 ? BigDecimal.ZERO
                : totalCost.divide(quantity, PositionCalculator.INTERNAL_SCALE, ROUNDING);
    }

    public BigDecimal marketValue(BigDecimal price) {
        return quantity.multiply(price).setScale(MONEY_SCALE, ROUNDING);
    }

    public BigDecimal unrealizedPnl(BigDecimal price) {
        return quantity.multiply(price).subtract(totalCost).setScale(MONEY_SCALE, ROUNDING);
    }

    /** Realized + unrealized + dividends. */
    public BigDecimal totalReturn(BigDecimal price) {
        return realizedPnl.add(unrealizedPnl(price)).add(dividendIncome).setScale(MONEY_SCALE, ROUNDING);
    }
}
