package com.portfolioiq.alert;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure alert logic, kept separate from persistence so it can be tested without a database. */
public final class AlertRules {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AlertRules() {}

    public static boolean isTriggered(AlertCondition condition, BigDecimal price, BigDecimal previousClose,
                                      BigDecimal targetPrice, BigDecimal thresholdPercent) {
        if (condition == null || price == null) return false;
        return switch (condition) {
            case ABOVE -> targetPrice != null && price.compareTo(targetPrice) >= 0;
            case BELOW -> targetPrice != null && price.compareTo(targetPrice) <= 0;
            case PERCENT_MOVE -> {
                if (thresholdPercent == null || previousClose == null || previousClose.signum() <= 0) yield false;
                BigDecimal move = price.subtract(previousClose).abs().multiply(HUNDRED)
                        .divide(previousClose, 6, RoundingMode.HALF_UP);
                yield move.compareTo(thresholdPercent) >= 0;
            }
        };
    }

    /** Throws IllegalArgumentException with a user-safe message when the alert definition is inconsistent. */
    public static void validate(AlertCondition condition, BigDecimal targetPrice, BigDecimal thresholdPercent) {
        if (condition == null) throw new IllegalArgumentException("Condition is required");
        if (condition == AlertCondition.PERCENT_MOVE) {
            if (targetPrice != null) throw new IllegalArgumentException("targetPrice does not apply to PERCENT_MOVE");
            if (thresholdPercent == null || thresholdPercent.signum() <= 0
                    || thresholdPercent.compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new IllegalArgumentException("thresholdPercent must be greater than 0 and at most 1000");
            }
            if (thresholdPercent.stripTrailingZeros().scale() > 4) {
                throw new IllegalArgumentException("thresholdPercent allows at most 4 decimal places");
            }
        } else {
            if (thresholdPercent != null) throw new IllegalArgumentException("thresholdPercent only applies to PERCENT_MOVE");
            if (targetPrice == null || targetPrice.signum() <= 0) {
                throw new IllegalArgumentException("targetPrice must be greater than 0");
            }
            BigDecimal s = targetPrice.stripTrailingZeros();
            if (s.scale() > 8 || s.precision() - s.scale() > 16) {
                throw new IllegalArgumentException("targetPrice has too many digits");
            }
        }
    }
}
