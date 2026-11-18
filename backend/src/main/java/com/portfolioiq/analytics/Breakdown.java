package com.portfolioiq.analytics;

import com.portfolioiq.finance.TradeEvent;

import java.math.BigDecimal;
import java.util.List;

public final class Breakdown {
    private Breakdown() {}

    /**
     * Total money put into an asset: buys including fees. Incoming transfers count only when requested, which is
     * right for a single portfolio but would double count shares when several of your own portfolios are combined.
     */
    public static BigDecimal totalBought(List<TradeEvent> events, boolean includeTransfersIn) {
        BigDecimal total = BigDecimal.ZERO;
        for (TradeEvent e : events) {
            if (e.type() == TradeEvent.Type.BUY) {
                total = total.add(e.quantity().multiply(e.unitPrice()).add(e.fee()));
            } else if (includeTransfersIn && e.type() == TradeEvent.Type.TRANSFER_IN) {
                total = total.add(e.quantity().multiply(e.unitPrice()));
            }
        }
        return total;
    }
}
