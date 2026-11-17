package com.portfolioiq.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One asset-level event in a portfolio.
 * For DIVIDEND, quantity is shares held and unitPrice is the dividend per share.
 * For TRANSFER_IN, unitPrice is the cost basis carried over per unit; TRANSFER_OUT ignores unitPrice.
 */
public record TradeEvent(Type type, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, Instant executedAt) {
    public enum Type { BUY, SELL, DIVIDEND, TRANSFER_IN, TRANSFER_OUT }

    public TradeEvent {
        if (type == null || quantity == null || unitPrice == null || fee == null || executedAt == null) {
            throw new IllegalArgumentException("All trade event fields are required");
        }
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("Unit price cannot be negative");
        if (fee.signum() < 0) throw new IllegalArgumentException("Fee cannot be negative");
    }

    boolean isAcquisition() { return type == Type.BUY || type == Type.TRANSFER_IN; }
}
