package com.portfolioiq.transaction;

import com.portfolioiq.finance.TradeEvent;
import com.portfolioiq.finance.TransactionType;

import java.util.EnumSet;
import java.util.Set;

/** Bridges stored transactions to the framework-free calculation core. */
public final class TradeEvents {
    /** Every transaction type that changes or earns from an asset position. */
    public static final Set<TransactionType> ASSET_TYPES = EnumSet.of(TransactionType.BUY, TransactionType.SELL,
            TransactionType.DIVIDEND, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT);

    private TradeEvents() {}

    public static TradeEvent from(Transaction t) {
        return new TradeEvent(TradeEvent.Type.valueOf(t.getType().name()), t.getQuantity(), t.getUnitPrice(),
                t.getFee(), t.getExecutedAt());
    }
}
