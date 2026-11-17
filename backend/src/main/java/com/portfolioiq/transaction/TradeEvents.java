package com.portfolioiq.transaction;

import com.portfolioiq.finance.TradeEvent;

import java.util.EnumSet;
import java.util.Set;

import com.portfolioiq.finance.TransactionType;

/** Bridges stored transactions to the framework-free calculation core. */
final class TradeEvents {
    static final Set<TransactionType> ASSET_TYPES =
            EnumSet.of(TransactionType.BUY, TransactionType.SELL, TransactionType.DIVIDEND);

    private TradeEvents() {}

    static TradeEvent from(Transaction t) {
        return new TradeEvent(TradeEvent.Type.valueOf(t.getType().name()), t.getQuantity(), t.getUnitPrice(),
                t.getFee(), t.getExecutedAt());
    }
}
