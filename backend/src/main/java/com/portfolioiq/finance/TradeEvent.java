package com.portfolioiq.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One asset-level event in a portfolio.
 * For DIVIDEND, quantity is shares held and unitPrice is the dividend per share.
 */
public record TradeEvent(Type type, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, Instant executedAt) {
    public enum Type { BUY, SELL, DIVIDEND }

    public TradeEvent {
        if (type == null || quantity == null || unitPrice == null || fee == null || executedAt == null) {
            throw new IllegalArgumentException("All trade event fields are required");
        }
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("Unit price cannot be negative");
        if (fee.signum() < 0) throw new IllegalArgumentException("Fee cannot be negative");
    }
}
