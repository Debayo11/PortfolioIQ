package com.portfolioiq.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure validation and total calculation for a transaction, kept free of framework code so it can be unit tested alone.
 * Throws IllegalArgumentException with a user-safe message on any invalid input.
 */
public final class TransactionRules {
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Normalised values ready to persist. total is always positive; its direction is implied by the type. */
    public record Computed(BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, BigDecimal total) {}

    private TransactionRules() {}

    public static boolean requiresAsset(TransactionType type) {
        return type == TransactionType.BUY || type == TransactionType.SELL || type == TransactionType.DIVIDEND;
    }

    public static Computed compute(TransactionType type, BigDecimal quantity, BigDecimal unitPrice,
                                   BigDecimal fee, BigDecimal amount) {
        if (type == null) throw new IllegalArgumentException("Transaction type is required");
        BigDecimal f = fee == null ? BigDecimal.ZERO : fee;
        if (f.signum() < 0) throw new IllegalArgumentException("Fee cannot be negative");
        check("Fee", f, 4, 15);

        if (requiresAsset(type)) {
            positive("Quantity", quantity);
            positive("Unit price", unitPrice);
            check("Quantity", quantity, 8, 16);
            check("Unit price", unitPrice, 8, 16);
            if (amount != null) {
                throw new IllegalArgumentException("Amount does not apply to " + type + "; provide quantity and unitPrice");
            }
            BigDecimal gross = quantity.multiply(unitPrice);
            BigDecimal total = type == TransactionType.BUY ? gross.add(f) : gross.subtract(f);
            if (total.signum() < 0) throw new IllegalArgumentException("Fee cannot exceed the transaction value");
            BigDecimal rounded = total.setScale(4, ROUNDING);
            check("Total amount", rounded, 4, 15);
            return new Computed(quantity.setScale(8, ROUNDING), unitPrice.setScale(8, ROUNDING),
                    f.setScale(4, ROUNDING), rounded);
        }

        positive("Amount", amount);
        check("Amount", amount, 4, 15);
        if (nonZero(quantity) || nonZero(unitPrice) || f.signum() != 0) {
            throw new IllegalArgumentException("Quantity, unit price and fee do not apply to " + type);
        }
        return new Computed(BigDecimal.ZERO.setScale(8), BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(4), amount.setScale(4, ROUNDING));
    }

    private static boolean nonZero(BigDecimal v) { return v != null && v.signum() != 0; }

    private static void positive(String name, BigDecimal v) {
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException(name + " must be greater than zero");
    }

    private static void check(String name, BigDecimal v, int maxScale, int maxIntegerDigits) {
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() > maxScale) {
            throw new IllegalArgumentException(name + " allows at most " + maxScale + " decimal places");
        }
        if (s.precision() - s.scale() > maxIntegerDigits) {
            throw new IllegalArgumentException(name + " is too large");
        }
    }
}
